import java.sql.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Explicit local database maintenance; credentials are read in-process, never printed or passed as arguments. */
public class DatabaseMaintenance {
    static Path root, privateDir;
    static Properties login;
    static Connection mysql() throws Exception {
        login = new Properties();
        try (Reader reader = Files.newBufferedReader(root.resolve("backend/guardrail-ready-policy-checker/.mysql-local.properties"))) { login.load(reader); }
        String user = login.getProperty("spring.datasource.username", "root"), password = login.getProperty("spring.datasource.password", "");
        return DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/legal_intake_lab?connectionTimeZone=Asia/Seoul&connectTimeout=5000&socketTimeout=30000",user,password);
    }
    static void store(Properties data, Path file) throws Exception { try (Writer writer=Files.newBufferedWriter(file)) {data.store(writer,"Local maintenance metadata; no row values or credentials");} }
    static Properties mysqlState(Connection c) throws Exception {
        if (!"legal_intake_lab".equals(c.getCatalog())) throw new IllegalStateException("Unexpected target schema");
        Properties p=new Properties();p.setProperty("database",c.getCatalog());p.setProperty("version",c.getMetaData().getDatabaseProductVersion());
        for(String table:List.of("legal_intake","consultation_memo")) {
            try(ResultSet r=c.createStatement().executeQuery("SELECT COUNT(*) FROM `"+table+"`")){r.next();p.setProperty(table+".count",r.getString(1));}
            try(PreparedStatement s=c.prepareStatement("SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION")){s.setString(1,table);try(ResultSet r=s.executeQuery()){while(r.next())p.setProperty(table+".column."+r.getString(1),r.getString(2)+";nullable="+r.getString(3)+";default="+r.getString(4));}}
            try(PreparedStatement s=c.prepareStatement("SELECT INDEX_NAME,NON_UNIQUE,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? GROUP BY INDEX_NAME,NON_UNIQUE")){s.setString(1,table);try(ResultSet r=s.executeQuery()){while(r.next())p.setProperty(table+".index."+r.getString(1),r.getInt(2)+":"+r.getString(3));}}
        }
        return p;
    }
    static boolean column(Connection c,String table,String name)throws Exception{try(PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?")){s.setString(1,table);s.setString(2,name);try(ResultSet r=s.executeQuery()){r.next();return r.getInt(1)>0;}}}
    static void mysqlMigrate(Connection c)throws Exception{
        Path backup=privateDir.resolve("mysql-before.sql"), cnf=privateDir.resolve("mysql-client.cnf");
        if(Files.exists(backup))throw new IllegalStateException("Backup file already exists; select a new maintenance directory");
        Properties before=mysqlState(c);store(before,privateDir.resolve("mysql-before.properties"));
        String password=login.getProperty("spring.datasource.password","").replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");
        Files.writeString(cnf,"[client]\nhost=127.0.0.1\nport=3306\nuser=\""+login.getProperty("spring.datasource.username","root")+"\"\npassword=\""+password+"\"\n");
        try{
            Process dump=new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe","--defaults-extra-file="+cnf,"--single-transaction","--hex-blob","--no-tablespaces","--set-gtid-purged=OFF","--default-character-set=utf8mb4","--result-file="+backup,"legal_intake_lab").redirectError(privateDir.resolve("mysql-dump.stderr.log").toFile()).start();
            if(dump.waitFor()!=0||!Files.exists(backup)||Files.size(backup)<100)throw new IllegalStateException("MySQL backup failed; migration not started");
        }finally{Files.deleteIfExists(cnf);}
        List<String[]> definitions=List.of(new String[]{"legal_intake","assigned_lawyer_username","VARCHAR(100) NULL"},new String[]{"consultation_memo","created_by","VARCHAR(100) NULL"},new String[]{"legal_intake","version","BIGINT NOT NULL DEFAULT 0"},new String[]{"legal_intake","updated_at","DATETIME(6) NULL"},new String[]{"legal_intake","idempotency_key","VARCHAR(120) NULL"},new String[]{"legal_intake","request_hash","VARCHAR(64) NULL"},new String[]{"consultation_memo","request_key","VARCHAR(120) NULL"},new String[]{"consultation_memo","content_hash","VARCHAR(64) NULL"});
        int added=0;
        for(String[] d:definitions)if(!column(c,d[0],d[1])){c.createStatement().execute("ALTER TABLE `"+d[0]+"` ADD COLUMN `"+d[1]+"` "+d[2]);added++;}
        Properties state=mysqlState(c);
        for(String[] index:List.of(new String[]{"legal_intake","uk_legal_intake_owner_idempotency","owner_username,idempotency_key"},new String[]{"consultation_memo","uk_consultation_memo_request_key","intake_id,request_key"})){
            String existing=state.getProperty(index[0]+".index."+index[1]);
            if(existing!=null&&!existing.equals("0:"+index[2]))throw new IllegalStateException("Existing index has an incompatible definition");
            if(existing==null){try(ResultSet r=c.createStatement().executeQuery("SELECT COUNT(*) FROM (SELECT "+index[2]+" FROM `"+index[0]+"` WHERE "+index[2].replace(","," IS NOT NULL AND ")+" IS NOT NULL GROUP BY "+index[2]+" HAVING COUNT(*)>1) duplicates")){r.next();if(r.getLong(1)!=0)throw new IllegalStateException("Duplicate keys require manual review");}c.createStatement().execute("ALTER TABLE `"+index[0]+"` ADD CONSTRAINT `"+index[1]+"` UNIQUE ("+index[2]+")");}
        }
        Properties after=mysqlState(c);for(String table:List.of("legal_intake","consultation_memo"))if(!before.getProperty(table+".count").equals(after.getProperty(table+".count")))throw new IllegalStateException("Unexpected row count change");
        after.setProperty("columnsAdded",Integer.toString(added));store(after,privateDir.resolve("mysql-after.properties"));
        System.out.println("MySQL backup + additive migration passed; columns added="+added+"; existing row counts unchanged");
    }
    static Connection h2(Path database)throws Exception{return DriverManager.getConnection("jdbc:h2:file:"+database.toString().replace('\\','/')+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE","sa","");}
    static void h2Restore(Path database,Path backup)throws Exception{
        if(!database.normalize().startsWith(privateDir)||!backup.normalize().startsWith(privateDir))throw new IllegalArgumentException("Private target required");
        Path marker=Path.of(database+".restored");if(Files.exists(marker))throw new IllegalStateException("Restored target cannot be cleared again");
        Properties manifest=new Properties();try(Reader reader=Files.newBufferedReader(backup.resolve("manifest.properties"))){manifest.load(reader);}
        try(Connection c=h2(database)){
            for(String table:manifest.getProperty("tables").split(","))if(!List.of("legal_procedure","legal_procedure_step","legal_form_template").contains(table))try(ResultSet r=c.createStatement().executeQuery("SELECT COUNT(*) FROM \""+table+"\"")){r.next();if(r.getLong(1)!=0)throw new IllegalStateException("Restore target contains non-seed data; refusing to clear it");}
            Statement s=c.createStatement();s.execute("ALTER TABLE legal_intake ALTER COLUMN version SET DEFAULT 0");s.execute("SET REFERENTIAL_INTEGRITY FALSE");
            // This target was just initialized for this restore; never the user's running database.
            for(String table:manifest.getProperty("tables").split(","))s.executeUpdate("DELETE FROM \""+table+"\"");
            s.execute("RUNSCRIPT FROM '"+backup.resolve("rows.sql").toString().replace('\\','/').replace("'","''")+"'");
            s.execute("SET REFERENTIAL_INTEGRITY TRUE");
            for(String table:manifest.getProperty("tables").split(",")){
                try(ResultSet r=c.getMetaData().getColumns(null,"public",table,null)){while(r.next())if("YES".equals(r.getString("IS_AUTOINCREMENT"))){String name=r.getString("COLUMN_NAME");try(ResultSet maximum=c.createStatement().executeQuery("SELECT COALESCE(MAX(\""+name+"\"),0)+1 FROM \""+table+"\"")){maximum.next();s.execute("ALTER TABLE \""+table+"\" ALTER COLUMN \""+name+"\" RESTART WITH "+maximum.getLong(1));}}}
            }
            verifyH2(c,manifest);Files.writeString(marker,"Rows and original-column SHA-256 digests verified against backup\n");
        }
        System.out.println("H2 restore passed: every original table count and original-column row digest matches");
    }
    static void verifyH2(Connection c,Properties manifest)throws Exception{
        for(String table:manifest.getProperty("tables").split(",")){
            String[] result=SnapshotAgent.digest(c,table,List.of(manifest.getProperty(table+".columns").split(",")),'"');
            if(!result[0].equals(manifest.getProperty(table+".count"))||!result[1].equals(manifest.getProperty(table+".sha256")))throw new IllegalStateException("Restored data mismatch: "+table);
        }
        for(String table:manifest.getProperty("tables").split(","))try(ResultSet keys=c.getMetaData().getImportedKeys(null,"public",table)){
            while(keys.next()){String child=keys.getString("FKCOLUMN_NAME"),parent=keys.getString("PKTABLE_NAME"),pk=keys.getString("PKCOLUMN_NAME");try(ResultSet r=c.createStatement().executeQuery("SELECT COUNT(*) FROM \""+table+"\" c LEFT JOIN \""+parent+"\" p ON c.\""+child+"\"=p.\""+pk+"\" WHERE c.\""+child+"\" IS NOT NULL AND p.\""+pk+"\" IS NULL")){r.next();if(r.getLong(1)!=0)throw new IllegalStateException("Foreign-key mismatch after restore");}}
        }
    }
    static void mysqlChecks(Connection c)throws Exception{
        Properties before=mysqlState(c);c.setAutoCommit(false);String key="migration-check-"+UUID.randomUUID();
        try{
            String insert="INSERT INTO legal_intake(session_id,owner_username,case_type,inquiry_purpose,party_position,core_statement,desired_next_step,dispute_area,client_guide_message,status,created_at,idempotency_key) VALUES ('migration-qa','migration-qa','CIVIL','절차 안내','원고','가상 이행 검수','절차 안내','대여금','가상 안내','SUBMITTED',CURRENT_TIMESTAMP(6),?)";
            long id;try(PreparedStatement s=c.prepareStatement(insert,Statement.RETURN_GENERATED_KEYS)){s.setString(1,key);s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()){r.next();id=r.getLong(1);}}
            boolean duplicate=false;try(PreparedStatement s=c.prepareStatement(insert)){s.setString(1,key);s.executeUpdate();}catch(SQLIntegrityConstraintViolationException expected){duplicate=true;}if(!duplicate)throw new IllegalStateException("Intake unique key not enforced");
            for(int i=0;i<2;i++)try(PreparedStatement s=c.prepareStatement(insert)){s.setNull(1,Types.VARCHAR);s.executeUpdate();}
            try(PreparedStatement s=c.prepareStatement("UPDATE legal_intake SET status='REVIEWED',version=version+1 WHERE id=? AND version=0")){s.setLong(1,id);if(s.executeUpdate()!=1||s.executeUpdate()!=0)throw new IllegalStateException("Version compare-and-set failed");}
            String memo="INSERT INTO consultation_memo(intake_id,content,created_at,follow_up_items,created_by,request_key,content_hash) VALUES (?,'가상 이행 메모',CURRENT_TIMESTAMP(6),'','migration-qa',?,'qa')";
            try(PreparedStatement s=c.prepareStatement(memo)){s.setLong(1,id);s.setString(2,key);s.executeUpdate();}
            duplicate=false;try(PreparedStatement s=c.prepareStatement(memo)){s.setLong(1,id);s.setString(2,key);s.executeUpdate();}catch(SQLIntegrityConstraintViolationException expected){duplicate=true;}if(!duplicate)throw new IllegalStateException("Memo unique key not enforced");
        }finally{c.rollback();c.setAutoCommit(true);}
        Properties after=mysqlState(c);for(String table:List.of("legal_intake","consultation_memo"))if(!before.getProperty(table+".count").equals(after.getProperty(table+".count")))throw new IllegalStateException("QA rollback did not restore row counts");
        System.out.println("MySQL unique keys, NULL legacy keys and version compare-and-set passed; synthetic writes rolled back");
    }
    static void mysqlRestoreCheck(Connection original)throws Exception{
        String schema="legal_intake_lab_restore_"+Long.toUnsignedString(System.currentTimeMillis());
        Path dump=privateDir.resolve("mysql-before.sql"),cnf=privateDir.resolve("mysql-restore-client.cnf");
        if(!Files.exists(dump)||Files.readString(dump).lines().anyMatch(line->line.matches("(?i)\\s*(USE |CREATE DATABASE).*")))throw new IllegalStateException("A schema-neutral backup is required");
        original.createStatement().execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
        String password=login.getProperty("spring.datasource.password","").replace("\\","\\\\").replace("\"","\\\"");
        Files.writeString(cnf,"[client]\nhost=127.0.0.1\nport=3306\nuser=\""+login.getProperty("spring.datasource.username","root")+"\"\npassword=\""+password+"\"\n");
        try{
            Process restore=new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe","--defaults-extra-file="+cnf,"--default-character-set=utf8mb4","--binary-mode",schema).redirectInput(dump.toFile()).redirectOutput(privateDir.resolve("mysql-restore.stdout.log").toFile()).redirectError(privateDir.resolve("mysql-restore.stderr.log").toFile()).start();
            if(restore.waitFor()!=0)throw new IllegalStateException("MySQL restore failed");
        }finally{Files.deleteIfExists(cnf);}
        int tables=0;
        try(Connection restored=DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/"+schema+"?connectionTimeZone=Asia/Seoul",login.getProperty("spring.datasource.username","root"),login.getProperty("spring.datasource.password",""));ResultSet names=restored.createStatement().executeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'")){
            while(names.next()){
                String table=names.getString(1);List<String> columns=new ArrayList<>();try(ResultSet r=restored.createStatement().executeQuery("SELECT * FROM `"+table+"` WHERE 1=0")){for(int i=1;i<=r.getMetaData().getColumnCount();i++)columns.add(r.getMetaData().getColumnName(i));}
                if(!Arrays.equals(SnapshotAgent.digest(original,table,columns,'`'),SnapshotAgent.digest(restored,table,columns,'`')))throw new IllegalStateException("MySQL restored data mismatch: "+table);tables++;
            }
        }
        Properties evidence=new Properties();evidence.setProperty("restoredSchema",schema);evidence.setProperty("tables",Integer.toString(tables));evidence.setProperty("originalRowsMatch","true");store(evidence,privateDir.resolve("mysql-restore-verification.properties"));
        System.out.println("MySQL backup restored into isolated schema; original-column data verified across "+tables+" tables; schema="+schema);
    }
    public static void main(String[] args) {
        try {
            root=Path.of(args[1]).toAbsolutePath().normalize();privateDir=root.resolve("backend/guardrail-ready-policy-checker/.local/deployment-2026-10-23");
            Files.createDirectories(privateDir);
            if(args[0].equals("mysql-inspect"))try(Connection c=mysql()){Properties p=mysqlState(c);store(p,privateDir.resolve("mysql-before.properties"));System.out.println("MySQL "+p.getProperty("version")+"; schema=legal_intake_lab; intake="+p.getProperty("legal_intake.count")+"; memo="+p.getProperty("consultation_memo.count"));}
            else if(args[0].equals("mysql-migrate"))try(Connection c=mysql()){mysqlMigrate(c);}
            else if(args[0].equals("mysql-checks"))try(Connection c=mysql()){mysqlChecks(c);mysqlRestoreCheck(c);}
            else if(args[0].equals("mysql-sql-check"))try(Connection c=mysql()){
                String sql=Files.readString(root.resolve("backend/guardrail-ready-policy-checker/src/main/resources/db/manual/2026-10-23-resubmit-version-mysql.sql")).lines().filter(line->!line.trim().startsWith("--")).reduce("",(a,b)->a+"\n"+b);
                for(int run=0;run<2;run++)for(String statement:sql.split(";"))if(!statement.isBlank())try(Statement s=c.createStatement()){s.execute(statement);}
                System.out.println("Guarded MySQL migration SQL executed twice successfully without repeated-column/index errors");
            }
            else if(args[0].equals("h2-restore"))h2Restore(Path.of(args[2]).toAbsolutePath().normalize(),Path.of(args[3]).toAbsolutePath().normalize());
            else if(args[0].equals("h2-schema"))try(Connection c=h2(Path.of(args[2]));Writer writer=Files.newBufferedWriter(Path.of(args[3]));ResultSet rows=c.createStatement().executeQuery("SCRIPT NODATA NOPASSWORDS NOSETTINGS")){
                writer.write("-- 2026-10-23 current H2 2.4.240 schema, data excluded; not a MySQL migration.\n");
                while(rows.next()){String statement=rows.getString(1);if(!statement.stripLeading().toUpperCase().startsWith("CREATE USER"))writer.write(statement+"\n");}
                System.out.println("Current H2 schema evidence exported without data or user credentials");
            }
            else throw new IllegalArgumentException("Unknown maintenance action");
        }catch(Exception error){System.err.println("Maintenance failed: "+error.getClass().getSimpleName()+(error instanceof SQLException sql?"; SQLState="+sql.getSQLState()+"; code="+sql.getErrorCode():""));System.exit(1);}
    }
}

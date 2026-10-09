import { readFile, writeFile } from "node:fs/promises";

const tokens = JSON.parse(await readFile("design/legal.tokens.json", "utf8"));
const toKebabCase = (value) => value.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`);
const cssAliases = {
  "color.background": "bg",
  "color.surface": "panel",
  "color.text": "text",
  "color.muted": "muted",
  "color.border": "line",
  "color.primary": "blue",
  "color.primarySoft": "blue-soft",
  "color.successSoft": "green-soft",
  "color.warningSoft": "yellow-soft",
  "color.dangerSoft": "red-soft",
  "color.code": "code"
};
const variables = Object.entries(tokens).flatMap(([group, values]) =>
  Object.entries(values).map(([name, value]) => {
    const key = `${group}.${name}`;
    return `  --${cssAliases[key] ?? `${group}-${toKebabCase(name)}`}: ${value};`;
  })
);

await writeFile(
  "src/tokens.css",
  `:root {\n${variables.join("\n")}\n}\n`,
  "utf8"
);

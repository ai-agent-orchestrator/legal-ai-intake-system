package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.entity.LegalIntake;
import com.ohgiraffers.handlermethod.repository.LegalIntakeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IntakeAccessService {
    private final LegalIntakeRepository repository;

    public IntakeAccessService(LegalIntakeRepository repository) { this.repository = repository; }

    public LegalIntake requireAccess(Long intakeId, Authentication authentication) {
        requireUser(authentication);
        LegalIntake intake = repository.findById(intakeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intake not found"));
        // Legacy anonymous records are visible only to the office administrator.
        boolean owner = intake.getOwnerUsername() != null && authentication.getName().equals(intake.getOwnerUsername());
        if (!isAdmin(authentication) && (intake.getOwnerUsername() == null || (!owner && !canManage(intake, authentication)))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Intake access denied");
        }
        return intake;
    }

    public String requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken
                || !(hasRole(authentication, "USER") || hasRole(authentication, "LAWYER") || isAdmin(authentication))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid user JWT required");
        }
        return authentication.getName();
    }

    public boolean isAdmin(Authentication authentication) { return hasRole(authentication, "ADMIN"); }
    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
    public boolean canManage(LegalIntake intake, Authentication authentication) {
        return isAdmin(authentication) || (intake.getOwnerUsername() != null && hasRole(authentication, "LAWYER")
                && authentication.getName().equals(intake.getAssignedLawyerUsername()));
    }
    public LegalIntake requireManager(Long id, Authentication authentication) {
        requireUser(authentication);
        // Status/memo changes and reassignment serialize on the same intake row.
        LegalIntake intake = repository.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intake not found"));
        if (!canManage(intake, authentication)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assigned lawyer or administrator required");
        return intake;
    }
    public void requireAdmin(Authentication authentication) {
        requireUser(authentication);
        if (!isAdmin(authentication)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Office administrator required");
    }
}

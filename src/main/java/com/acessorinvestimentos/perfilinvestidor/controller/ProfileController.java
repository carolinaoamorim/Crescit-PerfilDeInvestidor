package com.acessorinvestimentos.perfilinvestidor.controller;

import com.acessorinvestimentos.perfilinvestidor.dto.InvestorProfileResponse;
import com.acessorinvestimentos.perfilinvestidor.dto.QuestionnaireDto;
import com.acessorinvestimentos.perfilinvestidor.dto.QuestionnaireRequest;
import com.acessorinvestimentos.perfilinvestidor.service.InvestorProfileService;
import com.acessorinvestimentos.perfilinvestidor.service.QuestionnaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * O usuário é sempre o "sub" do JWT do Auth0. O {userId} da URL só é aceito
 * quando bate com o "sub": cada um consulta apenas o próprio perfil.
 */
@RestController
@RequestMapping("/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final QuestionnaireService questionnaireService;
    private final InvestorProfileService investorProfileService;

    @PostMapping("/questionnaire")
    public ResponseEntity<InvestorProfileResponse> submitQuestionnaire(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody QuestionnaireRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(questionnaireService.submitQuestionnaire(jwt.getSubject(), request));
    }

    @PutMapping("/questionnaire")
    public ResponseEntity<InvestorProfileResponse> updateQuestionnaire(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody QuestionnaireRequest request) {
        return ResponseEntity.ok(questionnaireService.updateQuestionnaire(jwt.getSubject(), request));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<InvestorProfileResponse> getProfile(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String userId) {
        requireOwner(jwt, userId);
        return ResponseEntity.ok(investorProfileService.getProfileByUserId(userId));
    }

    @GetMapping("/{userId}/questionnaire")
    public ResponseEntity<QuestionnaireDto> getQuestionnaire(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String userId) {
        requireOwner(jwt, userId);
        return ResponseEntity.ok(questionnaireService.getQuestionnaireByUserId(userId));
    }

    private void requireOwner(Jwt jwt, String userId) {
        if (!userId.equals(jwt.getSubject())) {
            throw new AccessDeniedException("You can only access your own profile");
        }
    }
}

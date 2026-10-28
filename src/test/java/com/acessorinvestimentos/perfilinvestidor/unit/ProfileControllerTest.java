package com.acessorinvestimentos.perfilinvestidor.unit;

import com.acessorinvestimentos.perfilinvestidor.config.SecurityConfig;
import com.acessorinvestimentos.perfilinvestidor.controller.ProfileController;
import com.acessorinvestimentos.perfilinvestidor.dto.InvestorProfileResponse;
import com.acessorinvestimentos.perfilinvestidor.dto.QuestionnaireDto;
import com.acessorinvestimentos.perfilinvestidor.exception.IncompleteQuestionnaireException;
import com.acessorinvestimentos.perfilinvestidor.exception.QuestionnaireAlreadyExistsException;
import com.acessorinvestimentos.perfilinvestidor.exception.ResourceNotFoundException;
import com.acessorinvestimentos.perfilinvestidor.model.enums.ExperienceLevel;
import com.acessorinvestimentos.perfilinvestidor.model.enums.FinancialGoal;
import com.acessorinvestimentos.perfilinvestidor.model.enums.InvestmentHorizon;
import com.acessorinvestimentos.perfilinvestidor.model.enums.InvestorType;
import com.acessorinvestimentos.perfilinvestidor.model.enums.RiskTolerance;
import com.acessorinvestimentos.perfilinvestidor.service.InvestorProfileService;
import com.acessorinvestimentos.perfilinvestidor.service.QuestionnaireService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
class ProfileControllerTest {

    private static final String USER_ID = "auth0|123";
    private static final String OTHER_USER_ID = "auth0|456";
    private static final String BODY = """
            {
              "financialGoal": "BUILD_WEALTH",
              "riskTolerance": "HIGH",
              "investmentHorizon": "LONG_TERM",
              "hasEmergencyReserve": true,
              "experienceLevel": "ADVANCED"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuestionnaireService questionnaireService;

    @MockitoBean
    private InvestorProfileService investorProfileService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    /** Token do Auth0 com o sub do usuário e as permissões informadas. */
    private static RequestPostProcessor token(String... permissions) {
        return jwt().jwt(j -> j.subject(USER_ID))
                .authorities(Arrays.stream(permissions)
                        .map(p -> (GrantedAuthority) new SimpleGrantedAuthority("SCOPE_" + p)).toList());
    }

    private InvestorProfileResponse profile(InvestorType type, int score) {
        LocalDateTime now = LocalDateTime.now();
        return new InvestorProfileResponse(UUID.randomUUID(), USER_ID, type, score, now, now);
    }

    // ---- 401: sem token ----

    @Test
    void submitQuestionnaire_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/profiles/questionnaire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(questionnaireService);
    }

    @Test
    void getProfile_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/profiles/{userId}", USER_ID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(investorProfileService);
    }

    // ---- 403: sem permissão ou perfil de outro usuário ----

    @Test
    void submitQuestionnaire_withoutWritePermission_returns403() throws Exception {
        mockMvc.perform(post("/profiles/questionnaire").with(token("read:profile"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(questionnaireService);
    }

    @Test
    void updateQuestionnaire_withoutWritePermission_returns403() throws Exception {
        mockMvc.perform(put("/profiles/questionnaire").with(token("read:profile"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(questionnaireService);
    }

    @Test
    void getProfile_withoutReadPermission_returns403() throws Exception {
        mockMvc.perform(get("/profiles/{userId}", USER_ID).with(token("write:profile")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(investorProfileService);
    }

    @Test
    void getProfile_ofAnotherUser_returns403() throws Exception {
        mockMvc.perform(get("/profiles/{userId}", OTHER_USER_ID).with(token("read:profile")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(investorProfileService);
    }

    @Test
    void getQuestionnaire_ofAnotherUser_returns403() throws Exception {
        mockMvc.perform(get("/profiles/{userId}/questionnaire", OTHER_USER_ID).with(token("read:profile")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(questionnaireService);
    }

    // ---- sucesso e erros de negócio ----

    @Test
    void submitQuestionnaire_usesSubFromToken_returns201WithProfile() throws Exception {
        when(questionnaireService.submitQuestionnaire(eq(USER_ID), any()))
                .thenReturn(profile(InvestorType.AGGRESSIVE, 11));

        mockMvc.perform(post("/profiles/questionnaire").with(token("write:profile"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.investorType").value("AGGRESSIVE"))
                .andExpect(jsonPath("$.score").value(11));
    }

    @Test
    void submitQuestionnaire_incomplete_returns400WithMissingQuestions() throws Exception {
        when(questionnaireService.submitQuestionnaire(eq(USER_ID), any()))
                .thenThrow(new IncompleteQuestionnaireException(List.of("riskTolerance")));

        mockMvc.perform(post("/profiles/questionnaire").with(token("write:profile"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"financialGoal\": \"RETIREMENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("All questions must be answered"))
                .andExpect(jsonPath("$.details[0]").value("riskTolerance"));
    }

    @Test
    void submitQuestionnaire_alreadyAnswered_returns409() throws Exception {
        when(questionnaireService.submitQuestionnaire(eq(USER_ID), any()))
                .thenThrow(new QuestionnaireAlreadyExistsException(USER_ID));

        mockMvc.perform(post("/profiles/questionnaire").with(token("write:profile"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void submitQuestionnaire_invalidAnswerValue_returns400() throws Exception {
        String invalid = BODY.replace("\"HIGH\"", "\"VERY_HIGH\"");

        mockMvc.perform(post("/profiles/questionnaire").with(token("write:profile"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(questionnaireService);
    }

    @Test
    void submitQuestionnaire_blankUserId_returns400() throws Exception {
        when(questionnaireService.submitQuestionnaire(any(), any()))
                .thenThrow(new IllegalArgumentException("userId is required"));

        mockMvc.perform(post("/profiles/questionnaire")
                        .with(jwt().jwt(j -> j.subject(" "))
                                .authorities(new SimpleGrantedAuthority(SecurityConfig.WRITE_PROFILE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("userId is required"));
    }

    @Test
    void updateQuestionnaire_returns200WithProfile() throws Exception {
        when(questionnaireService.updateQuestionnaire(eq(USER_ID), any()))
                .thenReturn(profile(InvestorType.MODERATE, 6));

        mockMvc.perform(put("/profiles/questionnaire").with(token("write:profile"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.investorType").value("MODERATE"));
    }

    @Test
    void updateQuestionnaire_neverAnswered_returns404() throws Exception {
        when(questionnaireService.updateQuestionnaire(eq(USER_ID), any()))
                .thenThrow(new ResourceNotFoundException("Questionnaire not found for user " + USER_ID));

        mockMvc.perform(put("/profiles/questionnaire").with(token("write:profile"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProfile_returns200() throws Exception {
        when(investorProfileService.getProfileByUserId(USER_ID)).thenReturn(profile(InvestorType.CONSERVATIVE, 2));

        mockMvc.perform(get("/profiles/{userId}", USER_ID).with(token("read:profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.investorType").value("CONSERVATIVE"));
    }

    @Test
    void getProfile_unknownUser_returns404() throws Exception {
        when(investorProfileService.getProfileByUserId(USER_ID))
                .thenThrow(new ResourceNotFoundException("Profile not found for user " + USER_ID));

        mockMvc.perform(get("/profiles/{userId}", USER_ID).with(token("read:profile")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void getQuestionnaire_returns200() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(questionnaireService.getQuestionnaireByUserId(USER_ID)).thenReturn(new QuestionnaireDto(
                UUID.randomUUID(), USER_ID, FinancialGoal.RETIREMENT, RiskTolerance.MEDIUM,
                InvestmentHorizon.LONG_TERM, true, ExperienceLevel.INTERMEDIATE, now, now));

        mockMvc.perform(get("/profiles/{userId}/questionnaire", USER_ID).with(token("read:profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialGoal").value("RETIREMENT"))
                .andExpect(jsonPath("$.hasEmergencyReserve").value(true));
    }

    // ---- CORS ----

    @Test
    void preflightFromFrontend_isAllowed() throws Exception {
        mockMvc.perform(options("/profiles/questionnaire")
                        .header(HttpHeaders.ORIGIN, "https://crescit-frontend.vercel.app")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://crescit-frontend.vercel.app"));
    }

    @Test
    void preflightFromUnknownOrigin_isRejected() throws Exception {
        mockMvc.perform(options("/profiles/questionnaire")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }
}

package com.converter.supportmessaging.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.common.api.PageResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.supportmessaging.dto.SendSupportMessageRequest;
import com.converter.supportmessaging.dto.SupportMessageResponse;
import com.converter.supportmessaging.dto.SupportThreadResponse;
import com.converter.supportmessaging.dto.SupportThreadSummaryResponse;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Messagerie SAV (retour client : "les utilisateurs doivent pouvoir faire des reclamations, les
 * admin repondent") -- un seul fil continu par utilisateur, visible et repondable par n'importe
 * quel administrateur.
 */
class SupportMessagingHttpIT extends AbstractOrderPipelineIT {

    private ResponseEntity<ApiResponse<SupportThreadResponse>> myThreadRaw(String userToken) {
        return restTemplate.exchange("/api/v1/support/thread", HttpMethod.GET, new HttpEntity<>(auth(userToken)),
                new ParameterizedTypeReference<ApiResponse<SupportThreadResponse>>() {
                });
    }

    private ResponseEntity<ApiResponse<SupportMessageResponse>> sendAsUserRaw(String userToken, String body) {
        return restTemplate.exchange("/api/v1/support/messages", HttpMethod.POST,
                new HttpEntity<>(new SendSupportMessageRequest(body), auth(userToken)),
                new ParameterizedTypeReference<ApiResponse<SupportMessageResponse>>() {
                });
    }

    private ResponseEntity<ApiResponse<SupportMessageResponse>> replyAsAdminRaw(
            String adminToken, java.util.UUID userId, String body) {
        return restTemplate.exchange("/api/admin/support/threads/" + userId + "/messages", HttpMethod.POST,
                new HttpEntity<>(new SendSupportMessageRequest(body), auth(adminToken)),
                new ParameterizedTypeReference<ApiResponse<SupportMessageResponse>>() {
                });
    }

    @Test
    void myThread_beforeAnyMessage_isEmptyButNeverMissing() {
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ApiResponse<SupportThreadResponse>> response = myThreadRaw(user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().messages()).isEmpty();
    }

    @Test
    void sendAsUser_thenAdminSeesItInTheThread() {
        String user = tokenFor(createUser(RoleCode.USER));
        String admin = adminToken();

        ResponseEntity<ApiResponse<SupportMessageResponse>> sent = sendAsUserRaw(user, "Mon paiement a ete rejete a tort.");
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sent.getBody().data().fromAdmin()).isFalse();

        SupportThreadResponse myThread = myThreadRaw(user).getBody().data();
        assertThat(myThread.messages()).hasSize(1);
        assertThat(myThread.messages().get(0).body()).isEqualTo("Mon paiement a ete rejete a tort.");

        ResponseEntity<ApiResponse<SupportThreadResponse>> adminView = restTemplate.exchange(
                "/api/admin/support/threads/" + myThread.userId() + "/messages", HttpMethod.GET,
                new HttpEntity<>(auth(admin)), new ParameterizedTypeReference<ApiResponse<SupportThreadResponse>>() {
                });
        assertThat(adminView.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminView.getBody().data().messages()).hasSize(1);
        assertThat(adminView.getBody().data().messages().get(0).fromAdmin()).isFalse();
    }

    @Test
    void adminReply_appearsInTheSameThreadAndNotifiesTheUser() {
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        String admin = adminToken();
        sendAsUserRaw(user, "Mon paiement a ete rejete a tort.");

        ResponseEntity<ApiResponse<SupportMessageResponse>> reply =
                replyAsAdminRaw(admin, userEntity.getId(), "Bonjour, nous regardons votre dossier.");

        assertThat(reply.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reply.getBody().data().fromAdmin()).isTrue();

        SupportThreadResponse myThread = myThreadRaw(user).getBody().data();
        assertThat(myThread.messages()).hasSize(2);
        assertThat(myThread.messages().get(1).fromAdmin()).isTrue();
        assertThat(myThread.messages().get(1).body()).isEqualTo("Bonjour, nous regardons votre dossier.");
    }

    @Test
    void sameUser_alwaysReusesTheSameThread_neverCreatesASecondOne() {
        String user = tokenFor(createUser(RoleCode.USER));
        sendAsUserRaw(user, "Premier message.");
        sendAsUserRaw(user, "Deuxieme message.");

        SupportThreadResponse thread = myThreadRaw(user).getBody().data();

        assertThat(thread.messages()).hasSize(2);
    }

    @Test
    void adminCanReplyBeforeTheUserEverWrote() {
        User userEntity = createUser(RoleCode.USER);
        String admin = adminToken();

        ResponseEntity<ApiResponse<SupportMessageResponse>> reply =
                replyAsAdminRaw(admin, userEntity.getId(), "Bonjour, comment pouvons-nous vous aider ?");

        assertThat(reply.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        SupportThreadResponse myThread = myThreadRaw(tokenFor(userEntity)).getBody().data();
        assertThat(myThread.messages()).hasSize(1);
        assertThat(myThread.messages().get(0).fromAdmin()).isTrue();
    }

    @Test
    void listThreadsForAdmin_showsUnreadFlagUntilAdminOpensTheThread() {
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        String admin = adminToken();
        sendAsUserRaw(user, "J'ai un souci avec mon transfert.");

        ResponseEntity<ApiResponse<PageResponse<SupportThreadSummaryResponse>>> beforeOpen = restTemplate.exchange(
                "/api/admin/support/threads", HttpMethod.GET, new HttpEntity<>(auth(admin)),
                new ParameterizedTypeReference<ApiResponse<PageResponse<SupportThreadSummaryResponse>>>() {
                });
        SupportThreadSummaryResponse summary = beforeOpen.getBody().data().content().stream()
                .filter(t -> t.userId().equals(userEntity.getId())).findFirst().orElseThrow();
        assertThat(summary.hasUnreadFromUser()).isTrue();
        assertThat(summary.lastMessageBody()).isEqualTo("J'ai un souci avec mon transfert.");
        assertThat(summary.lastMessageFromAdmin()).isFalse();

        restTemplate.exchange("/api/admin/support/threads/" + userEntity.getId() + "/messages", HttpMethod.GET,
                new HttpEntity<>(auth(admin)), new ParameterizedTypeReference<ApiResponse<SupportThreadResponse>>() {
                });

        ResponseEntity<ApiResponse<PageResponse<SupportThreadSummaryResponse>>> afterOpen = restTemplate.exchange(
                "/api/admin/support/threads", HttpMethod.GET, new HttpEntity<>(auth(admin)),
                new ParameterizedTypeReference<ApiResponse<PageResponse<SupportThreadSummaryResponse>>>() {
                });
        SupportThreadSummaryResponse afterSummary = afterOpen.getBody().data().content().stream()
                .filter(t -> t.userId().equals(userEntity.getId())).findFirst().orElseThrow();
        assertThat(afterSummary.hasUnreadFromUser()).isFalse();
    }

    @Test
    void anotherUsersThread_isNotAccessibleAsAUser() {
        // Pas d'endpoint client pour consulter le fil d'un tiers : /support/thread ne renvoie
        // toujours que le fil du user authentifie -- verifie ici en envoyant en tant que A puis
        // en consultant en tant que B.
        String userA = tokenFor(createUser(RoleCode.USER));
        String userB = tokenFor(createUser(RoleCode.USER));
        sendAsUserRaw(userA, "Message prive de A.");

        SupportThreadResponse threadB = myThreadRaw(userB).getBody().data();

        assertThat(threadB.messages()).isEmpty();
    }

    @Test
    void send_withBlankBody_isRejected() {
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/support/messages", HttpMethod.POST,
                new HttpEntity<>(new SendSupportMessageRequest("   "), auth(user)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void nonAdminUser_cannotAccessAdminSupportEndpoints() {
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/support/threads", HttpMethod.GET, new HttpEntity<>(auth(user)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}

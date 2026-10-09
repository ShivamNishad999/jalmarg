package org.growforge.jalmarg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.growforge.jalmarg.service.MailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Boots the whole app on an in-memory DB (demo seed on, dev-only OTP mode so codes are read
 * from MailService instead of an inbox) and walks the main journeys over HTTP.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smoke;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "jalmarg.storage.local-dir=target/test-uploads",
        "jalmarg.alerts.internal-scheduler=false",
        "jalmarg.monitor.enabled=false",
        "jalmarg.alerts.run-key=test-key",
        "jalmarg.auth.dev-otp-log=true",
        "jalmarg.auth.admin-email=admin@test.local",
        "jalmarg.auth.admin-password=AdminPass123"
})
@AutoConfigureMockMvc
class ApiSmokeTest {

    @Autowired MockMvc mvc;
    @Autowired MailService mail;
    final ObjectMapper json = new ObjectMapper();

    private String body(Object o) throws Exception { return json.writeValueAsString(o); }

    private void register(String email, String pw) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(java.util.Map.of("name", "Test User", "email", email, "password", pw))))
                .andExpect(status().isAccepted());
    }

    private void verify(String email) throws Exception {
        String code = mail.lastDevCode(email);
        assertNotNull(code, "dev code should exist");
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(body(java.util.Map.of("email", email, "code", code))))
                .andExpect(status().isOk());
    }

    private String login(String email, String pw) throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(java.util.Map.of("email", email, "password", pw))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(res);
        assertFalse(res.contains("passwordHash"));
        return n.get("token").asText();
    }

    @Test
    void publicReadsWork() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.email").value("dev-console"));
        mvc.perform(get("/api/reports/active")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))));
        mvc.perform(get("/api/hotspots")).andExpect(status().isOk());
        String route = "{\"routes\":[[[28.6300,77.2263],[28.6400,77.2263]],[[28.6300,77.2400],[28.6400,77.2400]]]}";
        mvc.perform(post("/api/routes/evaluate").contentType(MediaType.APPLICATION_JSON).content(route))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes[0].hazardCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.recommendedIndex").value(1));
    }

    @Test
    void writesNeedAnAccount() throws Exception {
        mvc.perform(multipart("/api/reports").param("latitude", "28.5").param("longitude", "77.3").param("depthLevel", "KNEE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mvc.perform(get("/api/authority/summary")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reports/1/confirm")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token-1234567890"))
                .andExpect(status().isUnauthorized());
    }

    /** One person must not be able to hide a flooded road: clearing needs 5 different people. */
    @Test
    void clearingNeedsFiveDifferentPeople() throws Exception {
        String[] tokens = new String[6];
        for (int i = 0; i < 6; i++) {
            String email = "voter" + i + "@test.local";
            String ip = "10.9.9." + (i + 1);
            mvc.perform(post("/api/auth/register").header("X-Forwarded-For", ip).contentType(MediaType.APPLICATION_JSON)
                            .content(body(java.util.Map.of("name", "Voter " + i, "email", email, "password", "Monsoon2026"))))
                    .andExpect(status().isAccepted());
            mvc.perform(post("/api/auth/verify-email").header("X-Forwarded-For", ip).contentType(MediaType.APPLICATION_JSON)
                            .content(body(java.util.Map.of("email", email, "code", mail.lastDevCode(email)))))
                    .andExpect(status().isOk());
            String res = mvc.perform(post("/api/auth/login").header("X-Forwarded-For", ip).contentType(MediaType.APPLICATION_JSON)
                            .content(body(java.util.Map.of("email", email, "password", "Monsoon2026"))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            tokens[i] = "Bearer " + json.readTree(res).get("token").asText();
        }
        String rep = mvc.perform(multipart("/api/reports").header("Authorization", tokens[0])
                        .param("latitude", "28.57").param("longitude", "77.32").param("depthLevel", "SHIN"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(rep).get("id").asLong();

        for (int i = 1; i <= 4; i++) {
            mvc.perform(post("/api/reports/" + id + "/clear").header("Authorization", tokens[i]))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.clearVotes").value(i))
                    .andExpect(jsonPath("$.clearVotesNeeded").value(5));
        }
        // same person voting twice does not count twice
        mvc.perform(post("/api/reports/" + id + "/clear").header("Authorization", tokens[4]))
                .andExpect(jsonPath("$.clearVotes").value(4));
        mvc.perform(post("/api/reports/" + id + "/clear").header("Authorization", tokens[5]))
                .andExpect(jsonPath("$.status").value("CLEARED"))
                .andExpect(jsonPath("$.clearVotes").value(5));
    }

    @Test
    void citizenJourney() throws Exception {
        String email = "riya@test.local";
        register(email, "Monsoon2026");

        // cannot sign in before verifying
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(java.util.Map.of("email", email, "password", "Monsoon2026"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        // wrong code is rejected, right code verifies
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(body(java.util.Map.of("email", email, "code", "000000".equals(mail.lastDevCode(email)) ? "111111" : "000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));
        verify(email);

        // wrong password
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(java.util.Map.of("email", email, "password", "wrongPass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        String token = login(email, "Monsoon2026");
        String bearer = "Bearer " + token;
        mvc.perform(get("/api/auth/me").header("Authorization", bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CITIZEN"))
                .andExpect(jsonPath("$.emailVerified").value(true));

        // file a report with a photo
        MockMultipartFile photo = new MockMultipartFile("photo", "p.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});
        String rep = mvc.perform(multipart("/api/reports").file(photo).header("Authorization", bearer)
                        .param("latitude", "28.4625").param("longitude", "77.4905")
                        .param("depthLevel", "KNEE").param("locationName", "Test road"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.depthFt").value(1.5))
                .andExpect(jsonPath("$.bikeSafe").value(false))
                .andExpect(jsonPath("$.area").value("Greater Noida"))
                .andExpect(jsonPath("$.photoUrl", startsWith("/api/photos/")))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(rep).get("id").asLong();

        // one vote per person
        mvc.perform(post("/api/reports/" + id + "/confirm").header("Authorization", bearer))
                .andExpect(jsonPath("$.confirmations").value(1));
        mvc.perform(post("/api/reports/" + id + "/confirm").header("Authorization", bearer))
                .andExpect(jsonPath("$.confirmations").value(1));

        // citizens cannot use authority tools
        mvc.perform(get("/api/authority/summary").header("Authorization", bearer)).andExpect(status().isForbidden());

        // alerts are tied to the account email
        mvc.perform(post("/api/alerts/subscriptions").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone-else@x.com\",\"label\":\"Home\",\"latitude\":28.63,\"longitude\":77.22}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/api/alerts/subscriptions").header("Authorization", bearer))
                .andExpect(jsonPath("$", hasSize(1)));

        // logout revokes the token on the server
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetRevokesSessions() throws Exception {
        String email = "arjun@test.local";
        register(email, "FirstPass1");
        verify(email);
        String old = "Bearer " + login(email, "FirstPass1");

        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(body(java.util.Map.of("email", email)))).andExpect(status().isAccepted());
        String code = mail.lastDevCode(email);
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(body(java.util.Map.of("email", email, "code", code, "newPassword", "short"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_WEAK"));
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(body(java.util.Map.of("email", email, "code", code, "newPassword", "SecondPass2"))))
                .andExpect(status().isOk());
        // code is single use
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(body(java.util.Map.of("email", email, "code", code, "newPassword", "ThirdPass3"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/auth/me").header("Authorization", old)).andExpect(status().isUnauthorized());
        login(email, "SecondPass2");
        // unknown email gets the same answer (no account enumeration)
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(body(java.util.Map.of("email", "nobody@test.local")))).andExpect(status().isAccepted());
    }

    @Test
    void authorityTools() throws Exception {
        String admin = "Bearer " + login("admin@test.local", "AdminPass123");
        mvc.perform(get("/api/authority/summary").header("Authorization", admin).param("area", "Noida"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.perDay", hasSize(30)));
        mvc.perform(get("/api/authority/report.pdf").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(patch("/api/authority/reports/1").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLEARED\",\"note\":\"Pump deployed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLEARED"))
                .andExpect(jsonPath("$.authorityNote").value("Pump deployed"));

        // promote a citizen to authority
        register("officer@test.local", "Officer2026");
        verify("officer@test.local");
        mvc.perform(post("/api/admin/users/role").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"officer@test.local\",\"role\":\"AUTHORITY\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("AUTHORITY"));
        String officer = "Bearer " + login("officer@test.local", "Officer2026");
        mvc.perform(get("/api/authority/summary").header("Authorization", officer)).andExpect(status().isOk());
        // but authority cannot hand out roles
        mvc.perform(post("/api/admin/users/role").header("Authorization", officer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"officer@test.local\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/alerts/run")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/demo-refresh").header("X-Jalmarg-Key", "test-key")).andExpect(status().isOk());
    }
}

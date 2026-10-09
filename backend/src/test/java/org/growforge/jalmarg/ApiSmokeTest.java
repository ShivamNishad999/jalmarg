package org.growforge.jalmarg;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Boots the whole app on an in-memory DB (with demo seed) and calls every main endpoint. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smoke;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "jalmarg.storage.local-dir=target/test-uploads",
        "jalmarg.alerts.internal-scheduler=false",
        "jalmarg.alerts.run-key=test-key"
})
@AutoConfigureMockMvc
class ApiSmokeTest {

    @Autowired
    MockMvc mvc;

    @Test
    void endToEnd() throws Exception {
        mvc.perform(get("/api/meta")).andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(false));

        // demo seed creates live reports
        mvc.perform(get("/api/reports/active")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))));

        // citizen report with photo + depth level
        MockMultipartFile photo = new MockMultipartFile("photo", "p.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});
        mvc.perform(multipart("/api/reports").file(photo)
                        .param("latitude", "28.4625").param("longitude", "77.4905")
                        .param("depthLevel", "KNEE").param("locationName", "Test road"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.depthFt").value(1.5))
                .andExpect(jsonPath("$.bikeSafe").value(false))
                .andExpect(jsonPath("$.severity").value("SEVERE"))
                .andExpect(jsonPath("$.area").value("Greater Noida"))
                .andExpect(jsonPath("$.photoUrl", startsWith("/api/photos/")));

        // missing photo and depth -> 400
        mvc.perform(multipart("/api/reports").param("latitude", "28.5").param("longitude", "77.3"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/reports/1/confirm")).andExpect(status().isOk());

        // route straight through Minto Bridge (demo SEVERE spot) must report a hazard
        String body = "{\"routes\":[[[28.6300,77.2263],[28.6400,77.2263]],[[28.6300,77.2400],[28.6400,77.2400]]]}";
        mvc.perform(post("/api/routes/evaluate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes[0].hazardCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.recommendedIndex").value(1));

        mvc.perform(get("/api/hotspots")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(20))));
        mvc.perform(get("/api/authority/summary").param("area", "Noida")).andExpect(status().isOk())
                .andExpect(jsonPath("$.perDay", hasSize(30)));
        mvc.perform(get("/api/authority/report.pdf")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mvc.perform(post("/api/alerts/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"T\",\"email\":\"t@example.com\",\"label\":\"Home\",\"latitude\":28.6347,\"longitude\":77.2263}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/alerts/run")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/demo-refresh").header("X-Jalmarg-Key", "test-key"))
                .andExpect(status().isOk());
    }
}

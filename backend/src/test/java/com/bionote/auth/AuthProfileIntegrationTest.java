package com.bionote.auth;

import com.bionote.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthProfileIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clean(){
        jdbc.update("UPDATE experiment_records SET current_review_id=NULL,final_revision_id=NULL"); jdbc.update("DELETE FROM revision_attachments"); jdbc.update("DELETE FROM reviews"); jdbc.update("DELETE FROM record_revisions"); jdbc.update("DELETE FROM attachments"); jdbc.update("DELETE FROM experiment_records");
        jdbc.update("DELETE FROM template_fields WHERE template_id IN (SELECT id FROM record_templates WHERE scope='PERSONAL')"); jdbc.update("DELETE FROM record_templates WHERE scope='PERSONAL'");
        jdbc.update("DELETE FROM audit_events"); jdbc.update("DELETE FROM notifications"); jdbc.update("DELETE FROM project_invitations");
        jdbc.update("DELETE FROM project_members"); jdbc.update("DELETE FROM projects"); users.deleteAll();
    }

    private String register(String email) throws Exception {
        String body="{\"displayName\":\"测试用户\",\"email\":\""+email+"\",\"password\":\"Password123!\"}";
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.email").value(email.trim().toLowerCase()))
                .andReturn().getResponse().getContentAsString().split("\\\"accessToken\\\":\\\"")[1].split("\\\"")[0];
    }

    @Test void registerNormalizesEmailAndHashesPassword() throws Exception {
        register(" User@Example.com ");
        var user=users.findByEmailNormalized("user@example.com").orElseThrow();
        assertThat(user.getPasswordHash()).doesNotContain("Password123!").startsWith("$2");
    }

    @Test void duplicateEmailIsConflict() throws Exception {
        register("User@Example.com");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"另一个\",\"email\":\" user@example.com \",\"password\":\"Password123!\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test void loginUsesEmailAndRejectsWrongPassword() throws Exception {
        register("login@example.com");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"LOGIN@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"login@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test void protectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void profileUpdateChangesOnlyDisplayName() throws Exception {
        String token=register("profile@example.com");
        mvc.perform(put("/api/v1/users/me").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"新姓名\",\"email\":\"hacker@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.displayName").value("新姓名"))
                .andExpect(jsonPath("$.data.email").value("profile@example.com"));
    }

    @Test void invalidAvatarsAreRejected() throws Exception {
        String token=register("avatar@example.com");
        mvc.perform(multipart("/api/v1/users/me/avatar").file(new MockMultipartFile("file","x.png","image/png",new byte[0]))
                .header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/users/me/avatar").file(new MockMultipartFile("file","x.svg","image/svg+xml","<svg/>".getBytes()))
                .header("Authorization","Bearer "+token)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
    }

    @Test void validPngAvatarCanBeRead() throws Exception {
        String token=register("image@example.com");
        byte[] png=new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,1,2,3};
        String response=mvc.perform(multipart("/api/v1/users/me/avatar").file(new MockMultipartFile("file","头像.png","image/png",png))
                .header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=response.split("\\\"id\\\":\\\"")[1].split("\\\"")[0];
        mvc.perform(get("/api/v1/users/"+id+"/avatar")).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));
    }
}

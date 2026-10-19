package com.example.bulletinboard.controller.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.example.bulletinboard.dto.contact.ContactLegalHoldRequest;
import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.AdminContactService;
import com.example.bulletinboard.exception.ContactNotFoundException;

@WebMvcTest(AdminContactApiController.class)
@Import(SecurityConfig.class)
class ContactLegalHoldApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminContactService service;
    @MockitoBean UserRepository users;
    private static final String URL="/api/admin/contacts/1/legal-hold";
    private static final String BODY="{\"held\":true,\"reason\":\"法的保存\",\"until\":\"2099-01-01T00:00:00\"}";
    @Test @WithMockUser(username="admin@example.com",roles="ADMIN") void setsHoldAndReturnsAdminOnlyFields() throws Exception {
        var contact=new Contact();contact.setId(1L);contact.setLegalHoldReason("法的保存");contact.setLegalHoldUntil(LocalDateTime.of(2099,1,1,0,0));
        when(service.updateLegalHold(eq(1L),any(),eq("admin@example.com"))).thenReturn(contact);
        mvc.perform(patch(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.legalHoldReason").value("法的保存")).andExpect(jsonPath("$.legalHoldUntil").value("2099-01-01T00:00:00"));
        verify(service).updateLegalHold(1L,new ContactLegalHoldRequest(true,"法的保存",LocalDateTime.of(2099,1,1,0,0)),"admin@example.com");
    }
    @Test @WithMockUser(username="admin@example.com",roles="ADMIN") void releasesHold() throws Exception {
        when(service.updateLegalHold(eq(1L),any(),eq("admin@example.com"))).thenReturn(new Contact());
        mvc.perform(patch(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"held\":false}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.legalHoldUntil").isEmpty());
    }
    @Test void anonymousIsRejected() throws Exception {
        mvc.perform(patch(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles="USER") void ordinaryUserIsRejected() throws Exception {
        mvc.perform(patch(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles="ADMIN") void csrfIsRequired() throws Exception {
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles="ADMIN") void heldIsRequired() throws Exception {
        mvc.perform(patch(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.held").isNotEmpty());verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles="ADMIN") void missingContactIs404() throws Exception {
        when(service.updateLegalHold(eq(1L),any(),anyString())).thenThrow(new ContactNotFoundException());
        mvc.perform(patch(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());
    }
    @Test @WithMockUser(roles="ADMIN") void dbFailureHasFixed500() throws Exception {
        when(service.updateLegalHold(eq(1L),any(),anyString())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("SELECT secret test@example.com"));
        var result=mvc.perform(patch(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isInternalServerError()).andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).doesNotContain("SELECT","test@example.com");
    }
}

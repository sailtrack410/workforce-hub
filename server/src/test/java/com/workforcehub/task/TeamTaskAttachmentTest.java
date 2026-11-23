package com.workforcehub.task;

import com.workforcehub.auth.security.EmployeeUserDetails;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.DatabaseIdGenerator;
import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.file.repository.AttachmentRow;
import com.workforcehub.file.repository.DownloadAttachmentRow;
import com.workforcehub.file.service.DownloadTicketExecutionService;
import com.workforcehub.file.storage.StorageObjectResource;
import com.workforcehub.file.storage.StorageProvider;
import com.workforcehub.file.storage.StorageProviderRegistry;
import com.workforcehub.task.repository.TeamTaskRepository;
import com.workforcehub.task.service.TeamTaskService;
import com.workforcehub.task.service.TeamTaskAttachmentService;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamTaskAttachmentTest {
    private JdbcTemplate jdbc;
    private DatabaseIdGenerator ids;
    private AttachmentRefRepository refs;
    private StorageProvider provider;
    private TeamTaskAttachmentService service;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:task_files_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V71__add_team_task_and_personal_todo.sql"),
            new ClassPathResource("db/migration/V73__add_team_task_collaboration.sql")).execute(source);
        jdbc.execute("CREATE TABLE org_user (id BIGINT PRIMARY KEY, real_name VARCHAR(64), is_deleted INT DEFAULT 0)");
        jdbc.update("INSERT INTO org_user (id,real_name) VALUES (1,'派发人'),(2,'负责人'),(3,'无关人')");
        jdbc.execute("CREATE TABLE sys_id_sequence (sequence_name VARCHAR(64) PRIMARY KEY, high_watermark BIGINT)");
        jdbc.update("INSERT INTO sys_id_sequence VALUES ('global',10000)"); ids = new DatabaseIdGenerator(source);
        jdbc.execute("CREATE TABLE sys_attachment_ref (id BIGINT PRIMARY KEY, file_object_id BIGINT, display_name VARCHAR(128), is_deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE sys_file_object (id BIGINT PRIMARY KEY, file_size BIGINT, mime_type VARCHAR(64), scan_status VARCHAR(32), is_deleted INT DEFAULT 0)");
        jdbc.update("INSERT INTO sys_attachment_ref (id,file_object_id,display_name) VALUES (9,7,'检查.txt')");
        jdbc.update("INSERT INTO sys_file_object (id,file_size,mime_type,scan_status) VALUES (7,3,'text/plain','CLEAN')");
        jdbc.execute("""
            CREATE TABLE sys_download_ticket (id BIGINT PRIMARY KEY, ticket_no VARCHAR(64), attachment_ref_id BIGINT,
              requester_type VARCHAR(32), requester_id BIGINT, download_type VARCHAR(32), download_url VARCHAR(512),
              expire_at DATETIME, status VARCHAR(32), updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)
            """);
        jdbc.update("INSERT INTO team_task (task_id,title,creator_id,assignee_id) VALUES (101,'任务',1,2),(102,'任务2',1,2)");
        refs = mock(AttachmentRefRepository.class); provider = mock(StorageProvider.class);
        when(provider.providerCode()).thenReturn("LOCAL");
        when(provider.loadObject("files", "safe/key")).thenReturn(new StorageObjectResource(new ByteArrayResource(new byte[]{1,2,3}), 3L));
        when(refs.getAttachmentOrThrow(9L)).thenReturn(upload(1L, "CLEAN"));
        when(refs.getDownloadAttachmentOrThrow(9L)).thenReturn(download("CLEAN", 0));
        service = new TeamTaskAttachmentService(jdbc, new TeamTaskService(new TeamTaskRepository(jdbc)), refs,
            new StorageProviderRegistry(List.of(provider)), new DownloadTicketExecutionService(jdbc));
        employee(1);
    }
    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); ids.close(); jdbc.execute("SHUTDOWN"); }

    @Test
    void onlyOwnPersonalFilesCanBeAttachedAndRepeatedBindingIsIdempotent() {
        when(refs.getAttachmentOrThrow(9L)).thenReturn(upload(2L, "CLEAN"));
        assertThrows(BizException.class, () -> service.attach(101L, 9L));
        when(refs.getAttachmentOrThrow(9L)).thenReturn(upload(1L, "CLEAN"));
        service.attach(101L,9L); service.attach(101L,9L);
        assertEquals(1,service.list(101L).size()); assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM team_task_attachment",Integer.class));
    }
    @Test
    void taskParticipantDownloadsThroughTaskTicketWithoutGainingPersonalFileAccess() {
        service.attach(101L,9L); employee(2);
        assertFalse(service.list(101L).get(0).canRemove());
        var ticket = service.createTicket(101L,9L);
        assertTrue(ticket.getDownloadUrl().startsWith("/api/employee/team-tasks/101/attachments/9/download/TKDL"));
        assertEquals(3,service.download(101L,9L,ticket.getTicketNo()).fileSize());
        assertThrows(BizException.class, () -> service.download(101L,9L,ticket.getTicketNo()));
        verify(provider,times(1)).loadObject("files","safe/key");
    }
    @Test
    void ticketsAreBoundToRequesterTaskAttachmentAndCurrentMembership() {
        service.attach(101L,9L); service.attach(102L,9L); employee(2);
        var ticket=service.createTicket(101L,9L);
        assertThrows(BizException.class,()->service.download(102L,9L,ticket.getTicketNo()));
        employee(1); assertThrows(BizException.class,()->service.download(101L,9L,ticket.getTicketNo()));
        employee(2); jdbc.update("UPDATE team_task SET assignee_id = 3 WHERE task_id = 101");
        assertThrows(BizException.class,()->service.download(101L,9L,ticket.getTicketNo()));
        verify(provider,never()).loadObject(anyString(),anyString());
    }
    @Test
    void unlinkRevokesIssuedTicketAndDoesNotDeletePersonalFile() {
        service.attach(101L,9L); var ticket=service.createTicket(101L,9L); service.remove(101L,9L);
        assertThrows(BizException.class,()->service.download(101L,9L,ticket.getTicketNo()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref",Integer.class));
    }
    @Test
    void scansDeletedObjectsAndExpiryAreRecheckedAtConsumption() {
        service.attach(101L,9L);
        when(refs.getDownloadAttachmentOrThrow(9L)).thenReturn(download("PENDING",0));
        assertThrows(BizException.class,()->service.createTicket(101L,9L));
        when(refs.getDownloadAttachmentOrThrow(9L)).thenReturn(download("CLEAN",0)); var ticket=service.createTicket(101L,9L);
        when(refs.getDownloadAttachmentOrThrow(9L)).thenReturn(download("FAILED",0));
        assertThrows(BizException.class,()->service.download(101L,9L,ticket.getTicketNo()));
        when(refs.getDownloadAttachmentOrThrow(9L)).thenReturn(download("CLEAN",1));
        assertThrows(BizException.class,()->service.download(101L,9L,ticket.getTicketNo()));
        jdbc.update("UPDATE sys_download_ticket SET expire_at = ?",LocalDateTime.now().minusMinutes(1));
        assertThrows(BizException.class,()->service.download(101L,9L,ticket.getTicketNo()));
    }
    @Test
    void unrelatedAndFormerParticipantsCannotListAttachOrIssueTickets() {
        employee(3);
        assertThrows(BizException.class,()->service.list(101L));
        assertThrows(BizException.class,()->service.attach(101L,9L));
        assertThrows(BizException.class,()->service.createTicket(101L,9L));
        verifyNoInteractions(refs);
    }
    private AttachmentRow upload(Long userId,String scan) {
        return new AttachmentRow(9L,7L,"PERSONAL_FILE",null,"检查.txt","USER",userId,null,null,LocalDateTime.now(),0,"text/plain",3L,"generated",scan);
    }
    private DownloadAttachmentRow download(String scan,int deleted) {
        return new DownloadAttachmentRow(9L,"PERSONAL_FILE","检查.txt","USER",1L,null,0,"LOCAL","files","safe/key","text/plain",3L,scan,deleted);
    }
    private void employee(long id) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new EmployeeUserDetails(id,id*10,"fixture","","用户","ACTIVE"),null,List.of())); }
}

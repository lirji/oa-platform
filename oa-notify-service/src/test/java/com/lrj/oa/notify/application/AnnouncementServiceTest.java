package com.lrj.oa.notify.application;

import com.lrj.oa.notify.NotifyProperties;
import com.lrj.oa.notify.api.dto.NotifyDtos;
import com.lrj.oa.notify.infrastructure.ws.SessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnnouncementServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void listLoadsReadReceiptsInOneBatchAfterAnnouncementQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RecipientIndex recipients = mock(RecipientIndex.class);
        ReadReceiptStore receipts = mock(ReadReceiptStore.class);
        AnnouncementService service = new AnnouncementService(jdbc, recipients, receipts,
                mock(SessionRegistry.class), new NotifyProperties());
        var first = view(11L); var second = view(12L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(first, second));
        when(recipients.resolveOne("user-1")).thenReturn(7);
        when(receipts.hasRead(List.of(11L, 12L), 7)).thenReturn(Map.of(11L, true, 12L, false));

        List<NotifyDtos.AnnouncementView> result = service.list("user-1", 20);

        assertThat(result).extracting(NotifyDtos.AnnouncementView::readByMe)
                .containsExactly(true, false);
        verify(receipts).hasRead(List.of(11L, 12L), 7);
    }

    private static NotifyDtos.AnnouncementView view(long id) {
        return new NotifyDtos.AnnouncementView(id, "title", "content", "publisher", "name",
                1, "PUBLISHED", OffsetDateTime.now(), null, null);
    }
}

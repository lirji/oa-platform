package com.lrj.oa.notify.application;

import com.lrj.oa.notify.NotifyProperties;
import com.lrj.oa.notify.api.dto.NotifyDtos;
import com.lrj.oa.notify.infrastructure.ws.SessionRegistry;
import com.lrj.oa.notify.infrastructure.mapper.NotifyMappers;
import com.lrj.oa.security.port.DataScopeAccessChecker;
import org.roaringbitmap.RoaringBitmap;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
                mock(SessionRegistry.class), new NotifyProperties(),
                mock(NotifyMappers.NotificationMapper.class), mock(DataScopeAccessChecker.class));
        var first = view(11L); var second = view(12L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(first, second));
        when(recipients.resolveOne("user-1")).thenReturn(7);
        RoaringBitmap audience = RoaringBitmap.bitmapOf(7);
        when(receipts.audienceBitmap(11L)).thenReturn(audience);
        when(receipts.audienceBitmap(12L)).thenReturn(audience);
        when(receipts.hasRead(List.of(11L, 12L), 7)).thenReturn(Map.of(11L, true, 12L, false));

        List<NotifyDtos.AnnouncementView> result = service.list("user-1", 20);

        assertThat(result).extracting(NotifyDtos.AnnouncementView::readByMe)
                .containsExactly(true, false);
        verify(receipts).hasRead(List.of(11L, 12L), 7);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAndMarkReadDoNotLeakAnnouncementToNonAudience() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RecipientIndex recipients = mock(RecipientIndex.class);
        ReadReceiptStore receipts = mock(ReadReceiptStore.class);
        AnnouncementService service = new AnnouncementService(jdbc, recipients, receipts,
                mock(SessionRegistry.class), new NotifyProperties(),
                mock(NotifyMappers.NotificationMapper.class), mock(DataScopeAccessChecker.class));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(view(11L)));
        when(recipients.resolveOne("outsider")).thenReturn(99);
        when(receipts.audienceBitmap(11L)).thenReturn(RoaringBitmap.bitmapOf(7));
        when(receipts.hasRead(List.of(), 99)).thenReturn(Map.of());
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Integer.class),
                any(Object[].class))).thenReturn(1);

        assertThat(service.list("outsider", 20)).isEmpty();
        assertThatThrownBy(() -> service.markRead(11L, "outsider"))
                .isInstanceOf(com.lrj.oa.common.exception.BusinessException.class)
                .hasMessageContaining("不是该公告的受众");
    }

    private static NotifyDtos.AnnouncementView view(long id) {
        return new NotifyDtos.AnnouncementView(id, "title", "content", "publisher", "name",
                1, "PUBLISHED", OffsetDateTime.now(), null, null);
    }
}

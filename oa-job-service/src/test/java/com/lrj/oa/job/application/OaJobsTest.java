package com.lrj.oa.job.application;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OaJobsTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void existingPartitionStillReceivesParentComments() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JobRunner runner = mock(JobRunner.class);
        OaJobs jobs = new OaJobs(jdbc, runner, 16);

        when(jdbc.queryForObject(
                contains("FROM pg_tables"), eq(Integer.class), eq("oa_att"), anyString()))
                .thenReturn(1);
        when(jdbc.queryForObject(
                contains("obj_description"), eq(String.class), eq("oa_att.punch_record")))
                .thenReturn("员工打卡事实表");
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any()))
                .thenReturn(List.of());

        assertThat(jobs.ensureMonthlyPartitions("oa_att", "punch_record", "punch_time", 1))
                .isZero();

        verify(jdbc, never()).execute(contains("CREATE TABLE"));
        verify(jdbc).execute(contains("COMMENT ON TABLE \"oa_att\".\"punch_record_"));
    }
}

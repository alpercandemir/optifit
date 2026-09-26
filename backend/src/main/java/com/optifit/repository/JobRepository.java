package com.optifit.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JobRepository {

    public record Job(String id, String owner, String key, String fingerprint, String status, long createdAt,
            long expiresAt, String result, String errorCode, String message) {
    }

    private static final RowMapper<Job> JOB_MAPPER = (row, rowNumber) -> new Job(row.getString("id"),
            row.getString("owner"), row.getString("request_key"), row.getString("fingerprint"), row.getString("status"),
            row.getLong("created_at"), row.getLong("expires_at"), row.getString("result_json"),
            row.getString("error_code"), row.getString("error_message"));

    private final JdbcTemplate jdbc;

    public Optional<Job> get(String id, String owner) {
        return jdbc.query("""
                SELECT * FROM recommendation_jobs
                WHERE id = ? AND owner = ? AND expires_at > ?
                """, JOB_MAPPER, id, owner, Instant.now().toEpochMilli()).stream().findFirst();
    }

    public Optional<Job> byKey(String key, String owner) {
        return jdbc.query("""
                SELECT * FROM recommendation_jobs
                WHERE request_key = ? AND owner = ? AND expires_at > ?
                """, JOB_MAPPER, key, owner, Instant.now().toEpochMilli()).stream().findFirst();
    }

    public void create(String id, String owner, String key, String fingerprint, long now, long expires) {
        jdbc.update("""
                INSERT INTO recommendation_jobs
                    (id, owner, request_key, fingerprint, status, created_at, expires_at)
                VALUES (?, ?, ?, ?, 'QUEUED', ?, ?)
                """, id, owner, key, fingerprint, now, expires);
    }

    public boolean active(String id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT COUNT(*) > 0 FROM recommendation_jobs
                WHERE id = ? AND status IN ('QUEUED', 'ANALYZING', 'SEARCHING') AND expires_at > ?
                """, Boolean.class, id, Instant.now().toEpochMilli()));
    }

    public void stage(String id, String status) {
        jdbc.update("""
                UPDATE recommendation_jobs SET status = ?
                WHERE id = ? AND status IN ('QUEUED', 'ANALYZING', 'SEARCHING')
                """, status, id);
    }

    public void finish(String id, String status, String json) {
        jdbc.update("""
                UPDATE recommendation_jobs SET status = ?, result_json = ?
                WHERE id = ? AND status IN ('QUEUED', 'ANALYZING', 'SEARCHING') AND expires_at > ?
                """, status, json, id, Instant.now().toEpochMilli());
    }

    public void fail(String id, String code, String message) {
        jdbc.update("""
                UPDATE recommendation_jobs SET status = 'FAILED', error_code = ?, error_message = ?
                WHERE id = ? AND status IN ('QUEUED', 'ANALYZING', 'SEARCHING')
                """, code, message, id);
    }

    public void delete(String id, String owner) {
        jdbc.update("DELETE FROM recommendation_jobs WHERE id=? AND owner=?", id, owner);
    }

    public void cleanup() {
        jdbc.update("DELETE FROM recommendation_jobs WHERE expires_at<=?", Instant.now().toEpochMilli());
    }

    public List<Job> timedOut(long before) {
        return jdbc.query("""
                SELECT * FROM recommendation_jobs
                WHERE created_at < ? AND status IN ('QUEUED', 'ANALYZING', 'SEARCHING')
                """, JOB_MAPPER, before);
    }
}

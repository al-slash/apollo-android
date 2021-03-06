package com.apollographql.apollo.cache.normalized.sql;

import com.apollographql.apollo.cache.ApolloCacheHeaders;
import com.apollographql.apollo.cache.CacheHeaders;
import com.apollographql.apollo.cache.normalized.Record;
import com.apollographql.apollo.cache.normalized.RecordFieldJsonAdapter;
import com.squareup.sqldelight.db.SqlDriver;
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static com.google.common.truth.Truth.assertThat;

public class SqlNormalizedCacheTest {

  public static final String STANDARD_KEY = "key";
  public static final String QUERY_ROOT_KEY = "QUERY_ROOT";
  public static final String FIELDS = "{\"fieldKey\": \"value\"}";
  private SqlNormalizedCache sqlStore;

  @Before
  public void setUp() {
    SqlDriver driver = new JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, new Properties());
    ApolloDatabase.Companion.getSchema().create(driver);
    sqlStore = new SqlNormalizedCacheFactory(driver).create(RecordFieldJsonAdapter.create());
    sqlStore.clearAll();
  }

  @Test
  public void testRecordCreation() {
    createRecord(STANDARD_KEY);
    assertThat(sqlStore.loadRecord(STANDARD_KEY, CacheHeaders.NONE))
        .isNotNull();
  }

  @Test
  public void testRecordCreation_root() {
    createRecord(QUERY_ROOT_KEY);
    assertThat(sqlStore.loadRecord(QUERY_ROOT_KEY, CacheHeaders.NONE))
        .isNotNull();
  }

  @Test
  public void testRecordSelection() {
    createRecord(STANDARD_KEY);
    Record record = sqlStore.selectRecordForKey(STANDARD_KEY);
    assertThat(record).isNotNull();
    assertThat(record.key()).isEqualTo(STANDARD_KEY);
  }

  @Test
  public void testRecordSelection_root() {
    createRecord(QUERY_ROOT_KEY);
    Record record = sqlStore.selectRecordForKey(QUERY_ROOT_KEY);
    assertThat(record).isNotNull();
    assertThat(record.key()).isEqualTo(QUERY_ROOT_KEY);
  }

  @Test
  public void testRecordSelection_recordNotPresent() {
    Record record = sqlStore.loadRecord(STANDARD_KEY, CacheHeaders.NONE);
    assertThat(record).isNull();
  }

  @Test
  public void testRecordMerge() {
    sqlStore.merge(Record.builder(STANDARD_KEY)
        .addField("fieldKey", "valueUpdated")
        .addField("newFieldKey", true).build(), CacheHeaders.NONE);
    Record record = sqlStore.selectRecordForKey(STANDARD_KEY);
    assertThat(record).isNotNull();
    assertThat(record.fields().get("fieldKey")).isEqualTo("valueUpdated");
    assertThat(record.fields().get("newFieldKey")).isEqualTo(true);
  }

  @Test
  public void testRecordDelete() {
    createRecord(STANDARD_KEY);
    sqlStore.merge(Record.builder(STANDARD_KEY)
        .addField("fieldKey", "valueUpdated")
        .addField("newFieldKey", true).build(), CacheHeaders.NONE);
    sqlStore.deleteRecord(STANDARD_KEY);
    Record record = sqlStore.selectRecordForKey(STANDARD_KEY);
    assertThat(record).isNull();
  }

  @Test
  public void testClearAll() {
    createRecord(QUERY_ROOT_KEY);
    createRecord(STANDARD_KEY);
    sqlStore.clearAll();
    assertThat(sqlStore.selectRecordForKey(QUERY_ROOT_KEY)).isNull();
    assertThat(sqlStore.selectRecordForKey(STANDARD_KEY)).isNull();
  }


  // Tests for StandardCacheHeader compliance

  @Test
  public void testHeader_evictAfterRead() {
    createRecord(STANDARD_KEY);
    Record record = sqlStore.loadRecord(STANDARD_KEY, CacheHeaders.builder()
        .addHeader(ApolloCacheHeaders.EVICT_AFTER_READ, "true").build());
    assertThat(record).isNotNull();
    Record nullRecord = sqlStore.loadRecord(STANDARD_KEY, CacheHeaders.builder()
        .addHeader(ApolloCacheHeaders.EVICT_AFTER_READ, "true").build());
    assertThat(nullRecord).isNull();
  }

  @Test
  public void testHeader_noCache() {
    sqlStore.merge(Record.builder(STANDARD_KEY).build(),
        CacheHeaders.builder().addHeader(ApolloCacheHeaders.DO_NOT_STORE, "true").build());
    final Record record = sqlStore.loadRecord(STANDARD_KEY, CacheHeaders.NONE);
    assertThat(record).isNull();
  }

  private void createRecord(String key) {
    sqlStore.createRecord(key, FIELDS);
  }
}

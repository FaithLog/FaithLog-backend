package com.faithlog.points;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="FAITHLOG_RUN_POSTGRES_FLYWAY_TEST",matches="true")
class StarterPointsMigrationTest {
 @Test void credits_existing_and_new_users_once_preserves_balances_and_rolls_back_with_signup() throws Exception {
  String base=System.getenv("FLYWAY_TEST_JDBC_URL"),user=System.getenv("FLYWAY_TEST_USERNAME"),password=System.getenv("FLYWAY_TEST_PASSWORD");
  String db="starter_points_"+UUID.randomUUID().toString().replace("-","");
  try(var c=DriverManager.getConnection(base,user,password);var s=c.createStatement()){s.execute("CREATE DATABASE "+db);}
  String url=base.substring(0,base.lastIndexOf('/')+1)+db;
  try {
   var before=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").target("31").load();
   before.migrate();
   try(var c=DriverManager.getConnection(url,user,password)) {
    long existing=account(c),noWallet=account(c);
    try(var s=c.prepareStatement("INSERT INTO point_wallets(user_id,balance,version) VALUES(?,75,4)")){s.setLong(1,existing);s.executeUpdate();}
    var current=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").load();
    assertThat(current.migrate().success).isTrue();
    assertCredit(c,existing,1075);assertCredit(c,noWallet,1000);
    assertThat(current.migrate().migrationsExecuted).isZero();
    assertCredit(c,existing,1075);
    long fresh=account(c);assertCredit(c,fresh,1000);
    try(var s=c.prepareStatement("UPDATE users SET name='renamed' WHERE id=?")){s.setLong(1,fresh);s.executeUpdate();}
    assertCredit(c,fresh,1000);
    c.setAutoCommit(false);
    long rolledBack=account(c);assertCredit(c,rolledBack,1000);c.rollback();c.setAutoCommit(true);
    try(var s=c.prepareStatement("SELECT count(*) FROM point_wallets WHERE user_id=?")){s.setLong(1,rolledBack);try(var rs=s.executeQuery()){rs.next();assertThat(rs.getLong(1)).isZero();}}
   }
  } finally {
   try(var c=DriverManager.getConnection(base,user,password);var s=c.createStatement()){s.execute("DROP DATABASE "+db+" WITH (FORCE)");}
  }
 }
 private static long account(Connection c)throws SQLException {
  try(var s=c.prepareStatement("INSERT INTO users(name,email,password_hash,role,is_active,created_at,updated_at) VALUES('QA',?,'test-only','USER',true,now(),now()) RETURNING id")){
   s.setString(1,UUID.randomUUID()+"@example.test");try(var rs=s.executeQuery()){rs.next();return rs.getLong(1);}
  }
 }
 private static void assertCredit(Connection c,long user,long balance)throws SQLException {
  try(var s=c.prepareStatement("SELECT w.balance,t.delta,t.balance_after FROM point_wallets w JOIN point_transactions t ON t.user_id=w.user_id WHERE w.user_id=? AND t.business_key='welcome:initial-1000:v1'")){
   s.setLong(1,user);try(var rs=s.executeQuery()){assertThat(rs.next()).isTrue();assertThat(rs.getLong(1)).isEqualTo(balance);assertThat(rs.getLong(2)).isEqualTo(1000);assertThat(rs.getLong(3)).isEqualTo(balance);assertThat(rs.next()).isFalse();}
  }
 }
}


package com.faithlog.companion;
import static org.assertj.core.api.Assertions.*;
import com.faithlog.points.PointWalletService;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.domain.entity.User;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs(outputDir="build/generated-snippets")
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="FAITHLOG_RUN_POSTGRES_FLYWAY_TEST",matches="true")
class CompanionPostgresQaTest {
 private static final String DB="economy_qa_"+UUID.randomUUID().toString().replace("-","");
 @DynamicPropertySource static void database(DynamicPropertyRegistry properties)throws Exception{
  String base=System.getenv("FLYWAY_TEST_JDBC_URL");String user=System.getenv("FLYWAY_TEST_USERNAME"),password=System.getenv("FLYWAY_TEST_PASSWORD");
  try(var c=DriverManager.getConnection(base,user,password);var s=c.createStatement()){s.execute("CREATE DATABASE "+DB);}
  String url=base.substring(0,base.lastIndexOf('/')+1)+DB;
  properties.add("spring.datasource.url",()->url);properties.add("spring.datasource.username",()->user);properties.add("spring.datasource.password",()->password);
  properties.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");properties.add("spring.flyway.enabled",()->true);properties.add("spring.jpa.hibernate.ddl-auto",()->"validate");
 }
 @Autowired com.faithlog.points.AttendanceService attendance;
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
 @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
 @Autowired UserRepository users;@Autowired PointWalletService wallet;@Autowired CompanionEconomyService economy;@Autowired GardenService gardens;@Autowired CompanionCatalogService catalog;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager tx;
 @Test void miracle_policy_catalog_inventory_and_sale_are_consistent(){
  Long u=account();
  assertThat(jdbc.queryForObject("select sum(weight) from companion_pool_entries where pool_version='standard-v2'",Long.class)).isEqualTo(130000000L);
  assertThat(jdbc.queryForObject("select weight from companion_pool_entries where pool_version='standard-v2' and design_id='jesus:miracle'",Long.class)).isEqualTo(13000L);
  assertThat(jdbc.queryForObject("select active from companion_pool_versions where pool_version='standard-v1'",Boolean.class)).isFalse();
  assertThat(jdbc.queryForObject("select count(*) from companion_pool_entries where pool_version='standard-v1'",Long.class)).isEqualTo(130L);
  jdbc.update("insert into user_companions(user_id,design_id,quantity,acquired_at) values (?,'jesus:miracle',2,now())",u);
  assertThat(catalog.inventory(u,new CompanionQuery("OWNED","MIRACLE",null,"RARITY",0,20)).items()).extracting(CompanionCatalogService.Design::designId).containsExactly("jesus:miracle");
  var result=economy.sell(u,"miracle-sale",new CompanionEconomyService.SaleRequest("jesus:miracle",1));
  assertThat(result.pointDelta()).isEqualTo(1000000L);
  assertThat(economy.sell(u,"miracle-sale",new CompanionEconomyService.SaleRequest("jesus:miracle",1))).isEqualTo(result);
 }
 @Test void current_backgrounds_are_free_and_saved_for_new_and_existing_members(){
  Long user=account();
  assertThat(catalog.backgrounds(user)).allMatch(CompanionCatalogService.Background::owned);
  for(String id:List.of("meadow","olive","shore","starlight")){
   var current=gardens.get(user);
   assertThat(gardens.save(user,new GardenUpdate(current.version(),id,List.of())).backgroundId()).isEqualTo(id);
   assertThat(gardens.get(user).backgroundId()).isEqualTo(id);
  }
  assertThat(catalog.backgrounds(user)).allMatch(CompanionCatalogService.Background::owned);
  assertThat(jdbc.queryForObject("select count(*) from user_backgrounds where user_id=?",Long.class,user)).isEqualTo(5);
  Long direct=account();
  assertThat(gardens.save(direct,new GardenUpdate(0,"meadow",List.of())).backgroundId()).isEqualTo("meadow");
 }
 private Long account(){return users.saveAndFlush(User.create("QA",UUID.randomUUID()+"@example.test","test-only")).id();}
 @Test void draw_replay_sale_garden_and_conflict_are_consistent(){
  Long u=account();
  var request=new CompanionEconomyService.DrawRequest("standard-v2",10);
  var first=economy.draw(u,"draw1",request);assertThat(first.designIds()).hasSize(10);assertThat(first.balance()).isZero();
  assertThat(economy.draw(u,"draw1",request)).isEqualTo(first);
  assertThat(economy.drawHistory(u,0)).hasSize(1);
  assertThat(economy.drawHistory(u,0).getFirst().result()).isEqualTo(first);
  assertThat(economy.drawHistory(account(),0)).isEmpty();
  assertThat(economy.drawHistory(u,1)).isEmpty();
  assertThatThrownBy(()->economy.drawHistory(u,-1)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->economy.draw(u,"draw1",new CompanionEconomyService.DrawRequest("standard-v2",1))).isInstanceOf(RuntimeException.class);
  assertThat(jdbc.queryForObject("select sum(quantity) from user_companions where user_id=?",Long.class,u)).isEqualTo(10);
  String design=first.designIds().getFirst();var current=gardens.get(u);
  var placed=gardens.save(u,new GardenUpdate(current.version(),null,List.of(new GardenUpdate.Placement(1,design,.5,.5))));
  assertThat(placed.placements()).hasSize(1);
  assertThatThrownBy(()->gardens.save(u,new GardenUpdate(current.version(),null,List.of()))).isInstanceOf(RuntimeException.class);
  assertThat(catalog.inventory(u,new CompanionQuery("UNEQUIPPED",null,null,null,0,100)).items()).noneMatch(d->d.designId().equals(design));
  jdbc.update("update user_companions set quantity=3 where user_id=? and design_id=?",u,design);
  var sale=economy.sell(u,"sale1",new CompanionEconomyService.SaleRequest(design,2));
  assertThat(economy.sell(u,"sale1",new CompanionEconomyService.SaleRequest(design,2))).isEqualTo(sale);
  assertThatThrownBy(()->economy.sell(u,"sale2",new CompanionEconomyService.SaleRequest(design,1))).isInstanceOf(RuntimeException.class);
  assertThat(jdbc.queryForObject("select quantity from user_companions where user_id=? and design_id=?",Long.class,u,design)).isEqualTo(1);
  assertThat(wallet.wallet(u).balance()).isEqualTo(sale.pointDelta());
 }
 @Test void parallel_same_key_debits_once()throws Exception{
  Long u=account();var pool=Executors.newFixedThreadPool(2);
  try{var gate=new CountDownLatch(1);Callable<CompanionEconomyService.Result> task=()->{gate.await();return economy.draw(u,"same-key",new CompanionEconomyService.DrawRequest("standard-v2",1));};var a=pool.submit(task);var b=pool.submit(task);gate.countDown();assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo(b.get(30,TimeUnit.SECONDS));assertThat(wallet.wallet(u).balance()).isEqualTo(900);}finally{pool.shutdownNow();}
 }
 @Test void outer_failure_rolls_back_wallet_inventory_and_operation(){
  Long u=account();wallet.settle(u,100,"qa-initial");
  assertThatThrownBy(()->new TransactionTemplate(tx).executeWithoutResult(status->{economy.draw(u,"rollback",new CompanionEconomyService.DrawRequest("standard-v2",1));throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
  assertThat(wallet.wallet(u).balance()).isEqualTo(1100);
  assertThat(jdbc.queryForObject("select count(*) from companion_operations where user_id=?",Long.class,u)).isZero();
  assertThat(jdbc.queryForObject("select count(*) from user_companions where user_id=?",Long.class,u)).isZero();
 }
 @Test void http_authentication_catalog_draw_replay_and_docs()throws Exception{
  String email=UUID.randomUUID()+"@example.test";
  Long u=users.saveAndFlush(User.create("HTTP QA",email,passwords.encode("1234"))).id();
  String login=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email",email,"password","1234")))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse().getContentAsString();
  String token=json.readTree(login).path("data").path("accessToken").asText();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/companions/catalog")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/companions/catalog").header("Authorization","Bearer "+token)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.length()").value(131)).andDo(org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document("companions-catalog"));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/companions/me/inventory").param("sort","invalid").header("Authorization","Bearer "+token)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/points/me/attendance")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/points/me/attendance").header("Authorization","Bearer "+token)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.balance").value(1010)).andDo(org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document("points-attendance-claim"));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/points/me/attendance").header("Authorization","Bearer "+token)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.balance").value(1010));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/points/me/attendance").header("Authorization","Bearer "+token)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.claimed").value(true)).andDo(org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document("points-attendance"));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/companions/sale-prices")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/companions/sale-prices").header("Authorization","Bearer "+token)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.length()").value(6)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].unitPrice").value(1010)).andDo(org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document("companions-sale-prices"));
  wallet.settle(u,100,"http-initial");
  String body="{\"poolVersion\":\"standard-v2\",\"count\":1}";
  String first=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/companions/me/draws").header("Authorization","Bearer "+token).header("Idempotency-Key","http-draw").contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andDo(org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document("companions-draw")).andReturn().getResponse().getContentAsString();
  String retry=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/companions/me/draws").header("Authorization","Bearer "+token).header("Idempotency-Key","http-draw").contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(json.readTree(first).path("data")).isEqualTo(json.readTree(retry).path("data"));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/companions/me/draws").header("Authorization","Bearer "+token)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.length()").value(1)).andDo(org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document("companions-draw-history"));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/points/me/wallet").header("Authorization","Bearer "+token)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.balance").value(1010)).andDo(org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document("points-wallet"));
 }

 @Test void concurrent_attendance_claims_credit_once_and_rollback_is_atomic()throws Exception {
  Long u=account();assertThat(attendance.status(u).claimed()).isFalse();
  var pool=Executors.newFixedThreadPool(2);
  try {
   var gate=new CountDownLatch(1);
   Callable<com.faithlog.points.AttendanceService.Result> task=()->{gate.await();return attendance.claim(u);};
   var a=pool.submit(task);var b=pool.submit(task);gate.countDown();
   assertThat(a.get(30,TimeUnit.SECONDS).balance()).isEqualTo(1010);
   assertThat(b.get(30,TimeUnit.SECONDS).balance()).isEqualTo(1010);
  }finally{pool.shutdownNow();}
  assertThat(attendance.status(u).claimed()).isTrue();
  assertThat(wallet.wallet(u).balance()).isEqualTo(1010);
  assertThat(jdbc.queryForObject("select count(*) from point_transactions where user_id=?",Long.class,u)).isEqualTo(2);
  Long other=account();assertThat(attendance.status(other).claimed()).isFalse();
  assertThatThrownBy(()->new TransactionTemplate(tx).executeWithoutResult(status->{attendance.claim(other);throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
  assertThat(attendance.status(other).claimed()).isFalse();
  assertThat(wallet.wallet(other).balance()).isEqualTo(1000);
 }

}

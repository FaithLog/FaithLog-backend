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
 @Autowired UserRepository users;@Autowired PointWalletService wallet;@Autowired CompanionEconomyService economy;@Autowired CompanionInstanceService instances;@Autowired GardenService gardens;@Autowired CompanionCatalogService catalog;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager tx;
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
 @Test void home_catalog_reads_are_side_effect_free_until_first_garden_write() {
  Long user=account();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM user_backgrounds WHERE user_id=?",
      Long.class,user)).isZero();
  assertThat(catalog.backgrounds(user)).allMatch(CompanionCatalogService.Background::owned);
  assertThat(catalog.backgrounds(user)).allMatch(CompanionCatalogService.Background::owned);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM user_backgrounds WHERE user_id=?",
      Long.class,user)).isZero();
  assertThat(economy.drawHistory(user,0)).isEmpty();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM user_backgrounds WHERE user_id=?",
      Long.class,user)).isZero();
  gardens.save(user,new GardenUpdate(0,"meadow",List.of()));
  assertThat(jdbc.queryForObject("SELECT count(*) FROM user_backgrounds WHERE user_id=?",
      Long.class,user)).isEqualTo(5L);
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
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/companions/sale-prices").header("Authorization","Bearer "+token)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.length()").value(6)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].unitPrice").value(10)).andDo(org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document("companions-sale-prices"));
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

 @Test void garden_get_is_read_only_and_initial_put_still_assigns_backgrounds() {
  Long user=account();
  for(int attempt=0;attempt<4;attempt++){
   var garden=gardens.get(user);
   assertThat(garden.version()).isZero();
   assertThat(garden.backgroundId()).isNull();
   assertThat(garden.placements()).isEmpty();
  }
  assertThat(jdbc.queryForObject("SELECT count(*) FROM user_gardens WHERE user_id=?",
          Long.class,user)).isZero();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM user_backgrounds WHERE user_id=?",
          Long.class,user)).isZero();
  var changed=gardens.save(user,new GardenUpdate(0,"meadow",List.of()));
  assertThat(changed.backgroundId()).isEqualTo("meadow");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM user_gardens WHERE user_id=?",
          Long.class,user)).isEqualTo(1L);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM user_backgrounds WHERE user_id=?",
          Long.class,user)).isEqualTo(5L);
  assertThat(gardens.get(user).version()).isEqualTo(1);
 }

 @Test void garden_query_is_capped_at_five_and_uses_ordered_paginated_inventory() {
  Long user=account();
  jdbc.update("INSERT INTO user_companions(user_id,design_id,quantity,acquired_at) "
          +"VALUES (?,'david-lamb:base',120,now())",user);
  jdbc.update("INSERT INTO user_companions(user_id,design_id,quantity,acquired_at) "
          +"VALUES (?,'hope-rabbit:base',80,now())",user);
  var first=instances.list(user,0,50);
  var second=instances.list(user,1,50);
  var last=instances.list(user,2,100);
  assertThat(first).hasSize(50);
  assertThat(second).hasSize(50);
  assertThat(last).hasSize(0);
  assertThat(first).extracting(CompanionInstanceService.View::instanceId)
          .doesNotContainAnyElementsOf(second.stream()
              .map(CompanionInstanceService.View::instanceId).toList());
  assertThat(instances.gardenInstances(user)).isEmpty();
  var garden=gardens.get(user);
  gardens.save(user,new GardenUpdate(garden.version(),null,List.of(
          new GardenUpdate.Placement(1,"david-lamb:base",.2,.2),
          new GardenUpdate.Placement(2,"hope-rabbit:base",.7,.7))));
  assertThat(instances.gardenInstances(user))
          .extracting(CompanionInstanceService.View::designId)
          .containsExactly("david-lamb:base","hope-rabbit:base");
  assertThat(instances.list(user,0,50)).hasSize(50);
  assertThatThrownBy(()->instances.list(user,0,101))
          .isInstanceOf(com.faithlog.global.exception.BusinessException.class);
  assertThatThrownBy(()->instances.list(user,101,50))
          .isInstanceOf(com.faithlog.global.exception.BusinessException.class);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM companion_instances WHERE user_id=?",
          Long.class,user)).isEqualTo(200L);
 }

 @Test void on_demand_single_design_browse_is_bounded_and_owner_scoped() {
  Long user=account();
  Long another=account();
  jdbc.update("INSERT INTO user_companions(user_id,design_id,quantity,acquired_at) "
          +"VALUES (?,'david-lamb:base',61,now())",user);
  jdbc.update("INSERT INTO user_companions(user_id,design_id,quantity,acquired_at) "
          +"VALUES (?,'hope-rabbit:base',14,now())",user);
  jdbc.update("INSERT INTO user_companions(user_id,design_id,quantity,acquired_at) "
          +"VALUES (?,'david-lamb:base',1,now())",another);
  var first=instances.listByDesign(user,"david-lamb:base",0,20);
  var second=instances.listByDesign(user,"david-lamb:base",1,20);
  var last=instances.listByDesign(user,"david-lamb:base",3,20);
  assertThat(first).hasSize(20);
  assertThat(second).hasSize(20);
  assertThat(last).hasSize(1);
  assertThat(first).extracting(CompanionInstanceService.View::instanceId)
          .doesNotContainAnyElementsOf(second.stream()
                  .map(CompanionInstanceService.View::instanceId).toList());
  assertThat(instances.listByDesign(user,"hope-rabbit:base",0,20)).hasSize(14);
  assertThat(instances.listByDesign(another,"david-lamb:base",0,20)).hasSize(1);
  assertThatThrownBy(()->instances.listByDesign(user,"david-lamb:base",0,51))
          .isInstanceOf(com.faithlog.global.exception.BusinessException.class);
 }

 @Test void old_client_cannot_erase_a_uuid_duplicate_garden_and_new_client_can_clear_it(){
   Long user=account();
   String design="hope-rabbit:base";
   jdbc.update("INSERT INTO user_companions(user_id,design_id,quantity,acquired_at) "
      +"VALUES (?,?,2,now())",user,design);
   var animals=instances.listByDesign(user,design,0,20);
   var updated=gardens.save(user,new GardenUpdate(0,null,List.of(
     new GardenUpdate.Placement(1,design,.2,.3,animals.get(0).instanceId()),
     new GardenUpdate.Placement(2,design,.8,.7,animals.get(1).instanceId())),
     GardenUpdate.INSTANCE_PROTOCOL));
   assertThat(updated.placements()).hasSize(2);
   assertThatThrownBy(()->gardens.save(user,new GardenUpdate(
      updated.version(),null,List.of())))
      .isInstanceOf(com.faithlog.global.exception.BusinessException.class);
   assertThatThrownBy(()->gardens.save(user,new GardenUpdate(
      updated.version(),null,List.of(new GardenUpdate.Placement(1,design,.5,.5)))))
      .isInstanceOf(com.faithlog.global.exception.BusinessException.class);
   assertThat(gardens.get(user).version()).isEqualTo(updated.version());
   assertThat(gardens.get(user).placements()).hasSize(2);
   var cleared=gardens.save(user,new GardenUpdate(
      updated.version(),null,List.of(),GardenUpdate.INSTANCE_PROTOCOL));
   assertThat(cleared.placements()).isEmpty();
 }

 @Test void sale_availability_is_owner_scoped_and_matches_protected_inventory(){
   Long user=account(),other=account();
   String design="david-lamb:base";
   jdbc.update("INSERT INTO user_companions(user_id,design_id,quantity,acquired_at) "
      +"VALUES (?,?,3,now())",user,design);
   var ids=instances.listByDesign(user,design,0,20);
   gardens.save(user,new GardenUpdate(0,null,List.of(
      new GardenUpdate.Placement(1,design,.2,.3,ids.get(0).instanceId())),
      GardenUpdate.INSTANCE_PROTOCOL));
   instances.care(user,ids.get(1).instanceId(),"fed-protected",
      new CompanionInstanceService.CareRequest("FEED",0));
   assertThat(economy.saleAvailability(user,design).ownedQuantity()).isEqualTo(3L);
   assertThat(economy.saleAvailability(user,design).sellableQuantity()).isEqualTo(1L);
   assertThat(economy.saleAvailability(other,design).sellableQuantity()).isZero();
   assertThat(economy.sell(user,"sell-only-one",
      new CompanionEconomyService.SaleRequest(design,1)).quantity()).isEqualTo(1L);
   assertThat(economy.saleAvailability(user,design).sellableQuantity()).isZero();
   assertThatThrownBy(()->economy.sell(user,"sell-fail-protected",
      new CompanionEconomyService.SaleRequest(design,1)))
      .isInstanceOf(com.faithlog.global.exception.BusinessException.class);
 }

 @Test void identical_designs_bind_to_separate_owned_uuids_and_keep_independent_growth() {
  Long user=account();
  String design="david-lamb:base";
  jdbc.update("INSERT INTO user_companions(user_id,design_id,quantity,acquired_at) "
          +"VALUES (?,?,3,now())",user,design);
  var sameStyle=instances.list(user,0,10);
  assertThat(sameStyle).hasSize(3);
  var id1=sameStyle.get(0).instanceId();
  var id2=sameStyle.get(1).instanceId();
  assertThat(id1).isNotEqualTo(id2);
  var before=gardens.get(user);
  var updated=gardens.save(user,new GardenUpdate(before.version(),null,List.of(
          new GardenUpdate.Placement(1,design,.18,.33,id1),
          new GardenUpdate.Placement(2,design,.77,.55,id2))));
  assertThat(updated.placements()).hasSize(2);
  assertThat(updated.placements()).extracting(GardenUpdate.Placement::instanceId)
          .containsExactly(id1,id2);
  assertThat(instances.gardenInstances(user))
          .extracting(CompanionInstanceService.View::instanceId)
          .containsExactly(id1,id2);
  var fed=instances.care(user,id2,"feed-selected",
          new CompanionInstanceService.CareRequest("FEED",0));
  assertThat(fed.growthXp()).isEqualTo(15);
  assertThat(instances.gardenInstances(user)).extracting(CompanionInstanceService.View::growthXp)
          .containsExactly(0L,15L);
  var reloaded=gardens.get(user);
  assertThat(reloaded.placements().get(0).x()).isEqualTo(.18);
  assertThat(reloaded.placements().get(1).y()).isEqualTo(.55);
  assertThatThrownBy(()->gardens.save(user,new GardenUpdate(reloaded.version(),null,List.of(
          new GardenUpdate.Placement(1,design,.2,.2,id1),
          new GardenUpdate.Placement(2,design,.3,.3,id1)))))
          .isInstanceOf(com.faithlog.global.exception.BusinessException.class);
  Long attacker=account();
  assertThatThrownBy(()->gardens.save(attacker,new GardenUpdate(0,null,List.of(
          new GardenUpdate.Placement(1,design,.3,.3,id1)))))
          .isInstanceOf(com.faithlog.global.exception.BusinessException.class);
  // Sale can only consume the untouched third animal; both placed UUIDs survive.
  assertThat(economy.sell(user,"sell-one-untouched",
          new CompanionEconomyService.SaleRequest(design,1)).quantity()).isEqualTo(1);
  assertThat(instances.gardenInstances(user))
          .extracting(CompanionInstanceService.View::instanceId).containsExactly(id1,id2);
  assertThatThrownBy(()->economy.sell(user,"sell-selected",
          new CompanionEconomyService.SaleRequest(design,1)))
          .isInstanceOf(com.faithlog.global.exception.BusinessException.class);
 }

 @Test void old_garden_payload_retains_same_instance_and_is_backwards_compatible() {
  Long user=account();
  jdbc.update("INSERT INTO user_companions(user_id,design_id,quantity,acquired_at) "
          +"VALUES (?,'hope-rabbit:base',2,now())",user);
  var one=gardens.save(user,new GardenUpdate(0,null,List.of(
          new GardenUpdate.Placement(1,"hope-rabbit:base",.2,.3))));
  var selected=one.placements().getFirst().instanceId();
  assertThat(selected).isNotNull();
  var moved=gardens.save(user,new GardenUpdate(one.version(),null,List.of(
          new GardenUpdate.Placement(1,"hope-rabbit:base",.4,.5))));
  assertThat(moved.placements().getFirst().instanceId()).isEqualTo(selected);
  assertThat(moved.placements().getFirst().x()).isEqualTo(.4);
 }

 @Test void individual_inventory_migrates_new_quantities_and_care_is_idempotent() {
  Long user=account();
  jdbc.update("INSERT INTO user_companions(user_id,design_id,quantity,acquired_at) "
          +"VALUES (?,'david-lamb:base',2,now())",user);
  assertThat(instances.list(user)).hasSize(2);
  jdbc.update("UPDATE user_companions SET quantity=3 WHERE user_id=? AND design_id='david-lamb:base'",user);
  assertThat(instances.list(user)).hasSize(3);
  var first=instances.list(user).getFirst();
  var fed=instances.care(user,first.instanceId(),"care-first",
          new CompanionInstanceService.CareRequest("FEED",0));
  assertThat(fed.growthXp()).isEqualTo(15);
  assertThat(fed.totalEarnedXp()).isEqualTo(15);
  assertThat(fed.careDays()).isEqualTo(1);
  assertThat(fed.level()).isEqualTo(1);
  assertThat(fed.version()).isEqualTo(1);
  assertThat(instances.care(user,first.instanceId(),"care-first",
          new CompanionInstanceService.CareRequest("FEED",0))).isEqualTo(fed);
  assertThatThrownBy(() -> instances.care(user,first.instanceId(),"care-second",
          new CompanionInstanceService.CareRequest("FEED",1)))
          .isInstanceOf(com.faithlog.global.exception.BusinessException.class);
  assertThatThrownBy(() -> instances.care(user,first.instanceId(),"care-first",
          new CompanionInstanceService.CareRequest("PLAY",0)))
          .isInstanceOf(com.faithlog.global.exception.BusinessException.class);
  Long otherUser=account();
  assertThatThrownBy(() -> instances.care(otherUser,first.instanceId(),"hack",
          new CompanionInstanceService.CareRequest("PET",0)))
          .isInstanceOf(com.faithlog.global.exception.BusinessException.class);
  var sale=economy.sell(user,"safe-sale",new CompanionEconomyService.SaleRequest("david-lamb:base",2));
  assertThat(sale.quantity()).isEqualTo(2);
  assertThat(instances.list(user)).hasSize(1);
  assertThat(instances.list(user).getFirst().instanceId()).isEqualTo(first.instanceId());
  assertThat(instances.list(user).getFirst().growthXp()).isEqualTo(15);
  assertThat(jdbc.queryForObject("SELECT sum(quantity) FROM user_companions WHERE user_id=?",
          Long.class,user)).isEqualTo(1);
 }

 @Test void server_rechecks_evolution_and_preserves_all_levels() {
  Long user=account();
  jdbc.update("INSERT INTO user_companions(user_id,design_id,quantity,acquired_at) "
          +"VALUES (?,'daniel-lion:royal',1,now())",user);
  var animal=instances.list(user).getFirst();
  assertThat(animal.canEvolve()).isFalse();
  assertThatThrownBy(() -> instances.evolve(user,animal.instanceId(),"early",
          new CompanionInstanceService.EvolveRequest(0)))
          .isInstanceOf(com.faithlog.global.exception.BusinessException.class);
  jdbc.update("UPDATE companion_instances SET growth_xp=300,total_earned_xp=300,"
          +"care_days=3,satiety=100,cleanliness=100,health=100 WHERE id=?",
          animal.instanceId());
  var grown=instances.evolve(user,animal.instanceId(),"first-evolution",
          new CompanionInstanceService.EvolveRequest(0));
  assertThat(grown.level()).isEqualTo(2);
  assertThat(grown.growthXp()).isEqualTo(300);
  assertThat(grown.designId()).isEqualTo("daniel-lion:royal");
  assertThat(instances.evolve(user,animal.instanceId(),"first-evolution",
          new CompanionInstanceService.EvolveRequest(0))).isEqualTo(grown);
  jdbc.update("UPDATE companion_instances SET growth_xp=1000,total_earned_xp=1000,"
          +"care_days=10,satiety=100,cleanliness=100,health=100 WHERE id=?",
          animal.instanceId());
  var finalForm=instances.evolve(user,animal.instanceId(),"final-evolution",
          new CompanionInstanceService.EvolveRequest(1));
  assertThat(finalForm.level()).isEqualTo(3);
  assertThat(finalForm.growthXp()).isEqualTo(1000);
  assertThat(finalForm.canEvolve()).isFalse();
  assertThatThrownBy(() -> instances.evolve(user,animal.instanceId(),"extra",
          new CompanionInstanceService.EvolveRequest(2)))
          .isInstanceOf(com.faithlog.global.exception.BusinessException.class);
 }

 @Test void lazy_xp_loss_settles_on_care_without_charging_a_second_time() {
  Long user=account();
  jdbc.update("INSERT INTO user_companions(user_id,design_id,quantity,acquired_at) "
          +"VALUES (?,'hope-rabbit:base',1,now())",user);
  var animal=instances.list(user).getFirst();
  jdbc.update("UPDATE companion_instances SET growth_xp=750,total_earned_xp=750,"
          +"level=2,care_days=10,last_cared_at=now()-interval '120 hours',"
          +"last_evaluated_at=now()-interval '120 hours',elapsed_hours=0,xp_decay_days=0 "
          +"WHERE id=?",animal.instanceId());
  var preview=instances.list(user).getFirst();
  assertThat(preview.growthXp()).isEqualTo(710);
  assertThat(preview.totalEarnedXp()).isEqualTo(750);
  assertThat(jdbc.queryForObject("SELECT growth_xp FROM companion_instances WHERE id=?",
          Long.class,animal.instanceId())).isEqualTo(750);
  var cared=instances.care(user,animal.instanceId(),"return-to-care",
          new CompanionInstanceService.CareRequest("FEED",0));
  assertThat(cared.growthXp()).isEqualTo(725);
  assertThat(cared.totalEarnedXp()).isEqualTo(765);
  assertThat(cared.version()).isEqualTo(1);
  assertThat(instances.list(user).getFirst().growthXp()).isEqualTo(725);
  assertThat(instances.care(user,animal.instanceId(),"return-to-care",
          new CompanionInstanceService.CareRequest("FEED",0))).isEqualTo(cared);
  assertThat(jdbc.queryForObject("SELECT xp_decay_days FROM companion_instances WHERE id=?",
          Long.class,animal.instanceId())).isZero();
 }

 @Test void a_fainted_animal_can_sleep_and_recover_instead_of_being_deleted() {
  Long user=account();
  jdbc.update("INSERT INTO user_companions(user_id,design_id,quantity,acquired_at) "
          +"VALUES (?,'peace-dove:base',1,now())",user);
  var animal=instances.list(user).getFirst();
  jdbc.update("UPDATE companion_instances SET health=0,satiety=0,cleanliness=0 WHERE id=?",
          animal.instanceId());
  assertThatThrownBy(() -> instances.care(user,animal.instanceId(),"cant-play",
          new CompanionInstanceService.CareRequest("PLAY",0)))
          .isInstanceOf(com.faithlog.global.exception.BusinessException.class);
  var slept=instances.care(user,animal.instanceId(),"restore",
          new CompanionInstanceService.CareRequest("SLEEP",0));
  assertThat(slept.stats().health()).isEqualTo(15);
  assertThat(slept.growthXp()).isZero();
  assertThat(instances.list(user)).hasSize(1);
 }

}

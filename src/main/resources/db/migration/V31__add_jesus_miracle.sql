-- New policy preserves old operations and v1 probability snapshots.
ALTER TABLE companion_designs DROP CONSTRAINT companion_designs_rarity_check;
ALTER TABLE companion_designs ADD CONSTRAINT companion_designs_rarity_check CHECK(rarity IN ('COMMON','UNCOMMON','RARE','EPIC','UNIQUE','MIRACLE'));
ALTER TABLE companion_sale_prices DROP CONSTRAINT companion_sale_prices_rarity_check;
ALTER TABLE companion_sale_prices ADD CONSTRAINT companion_sale_prices_rarity_check CHECK(rarity IN ('COMMON','UNCOMMON','RARE','EPIC','UNIQUE','MIRACLE'));
INSERT INTO companion_designs(design_id,species_code,species_name,style_code,name,rarity,asset_key,catalog_order)
VALUES ('jesus:miracle','jesus','예수님','miracle','예수님','MIRACLE','jesus:miracle',130);
UPDATE companion_pool_versions SET active=false WHERE active=true;
INSERT INTO companion_pool_versions VALUES('standard-v2','미라클 동물 뽑기',100,true);
-- 13,000 original tickets × 9,999 + 13,000 miracle = 130,000,000.
-- Miracle = 0.01%; remaining tiers retain their relative distribution.
INSERT INTO companion_pool_entries(pool_version,design_id,weight)
SELECT 'standard-v2',design_id,weight*9999 FROM companion_pool_entries WHERE pool_version='standard-v1';
INSERT INTO companion_pool_entries VALUES('standard-v2','jesus:miracle',13000);
INSERT INTO companion_sale_prices VALUES('MIRACLE',1000000,'sale-miracle-v1');

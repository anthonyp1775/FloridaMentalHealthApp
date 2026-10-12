-- =====================================================================
-- CarePath FL - Seed / demo data
--
-- Run AFTER schema.sql.
--
-- DATA NOTES:
--  * All providers, organizations, clients and referrals are FICTIONAL.
--    Every organization name is prefixed "Example" on purpose. This
--    table stores open_slots, waitlist_count, accepting_new_clients and
--    insurance participation - attaching invented values for any of
--    those to a REAL clinic's name would be a fabricated record about a
--    real place, which is worse than useless in a demo. The prefix
--    makes the sample nature unmistakable at a glance, and pairs with
--    the reserved example.org websites below.
--  * Resources that ARE real and accurate (988, Crisis Text Line, 211,
--    SAMHSA, NAMI Florida) live in the frontend's Statewide Resources
--    page, deliberately separate from this synthetic directory.
--  * County names are real (all 67). Region and managing-entity values
--    are approximate groupings for demo purposes - VERIFY against the
--    current DCF region map and Managing Entity assignments before
--    presenting them as fact.
--  * Insurance plan names reflect carriers operating in Florida;
--    plan participation per provider is fictional.
--  * No clinical data of any kind is stored.
-- =====================================================================

USE fl_mental_health_db;

SET SQL_SAFE_UPDATES = 0;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE referral_status_history;
TRUNCATE TABLE referral_requests;
TRUNCATE TABLE saved_providers;
TRUNCATE TABLE provider_insurance;
TRUNCATE TABLE providers;
TRUNCATE TABLE organizations;
TRUNCATE TABLE insurance_plans;
TRUNCATE TABLE counties;
TRUNCATE TABLE user_roles;
TRUNCATE TABLE users;
TRUNCATE TABLE roles;
SET FOREIGN_KEY_CHECKS = 1;


-- =====================================================================
-- ROLES + USERS
-- =====================================================================

INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_USER');

INSERT INTO users (first_name, last_name, email, password) VALUES
    -- Navigator / clinic staff
    ('Dana',    'Whitfield', 'navigator@carepathfl.org', '$2b$11$ZqsiGWtoFxowdZxRcsvCIu0n/cO6BrCXFO6T5DoHmdpJuWTKrUhUO'),
    ('Marcus',  'Hale',      'intake@carepathfl.org',    '$2b$11$ZqsiGWtoFxowdZxRcsvCIu0n/cO6BrCXFO6T5DoHmdpJuWTKrUhUO'),
    -- People seeking care
    ('Alicia',  'Moreno',    'alicia.moreno@example.com','$2b$11$ZqsiGWtoFxowdZxRcsvCIu0n/cO6BrCXFO6T5DoHmdpJuWTKrUhUO'),
    ('Devon',   'Carter',    'devon.carter@example.com', '$2b$11$ZqsiGWtoFxowdZxRcsvCIu0n/cO6BrCXFO6T5DoHmdpJuWTKrUhUO'),
    ('Rosalie', 'Jean',      'rosalie.jean@example.com', '$2b$11$ZqsiGWtoFxowdZxRcsvCIu0n/cO6BrCXFO6T5DoHmdpJuWTKrUhUO'),
    ('Tyler',   'Brandt',    'tyler.brandt@example.com', '$2b$11$ZqsiGWtoFxowdZxRcsvCIu0n/cO6BrCXFO6T5DoHmdpJuWTKrUhUO');

-- Staff get BOTH roles (they can also browse as a user).
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.email IN ('navigator@carepathfl.org', 'intake@carepathfl.org');

-- Everyone else gets ROLE_USER only.
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.email NOT IN ('navigator@carepathfl.org', 'intake@carepathfl.org')
  AND r.name = 'ROLE_USER';


-- =====================================================================
-- COUNTIES - all 67
-- Region values group counties for demo filtering. Managing entities
-- are the regional behavioral-health contractors; verify current
-- assignments before citing them.
-- =====================================================================

INSERT INTO counties (name, region, managing_entity) VALUES
    ('Escambia',    'Northwest', 'Big Bend Community Based Care'),
    ('Santa Rosa',  'Northwest', 'Big Bend Community Based Care'),
    ('Okaloosa',    'Northwest', 'Big Bend Community Based Care'),
    ('Walton',      'Northwest', 'Big Bend Community Based Care'),
    ('Holmes',      'Northwest', 'Big Bend Community Based Care'),
    ('Washington',  'Northwest', 'Big Bend Community Based Care'),
    ('Bay',         'Northwest', 'Big Bend Community Based Care'),
    ('Jackson',     'Northwest', 'Big Bend Community Based Care'),
    ('Calhoun',     'Northwest', 'Big Bend Community Based Care'),
    ('Gulf',        'Northwest', 'Big Bend Community Based Care'),
    ('Liberty',     'Northwest', 'Big Bend Community Based Care'),
    ('Franklin',    'Northwest', 'Big Bend Community Based Care'),
    ('Gadsden',     'Northwest', 'Big Bend Community Based Care'),
    ('Leon',        'Northwest', 'Big Bend Community Based Care'),
    ('Wakulla',     'Northwest', 'Big Bend Community Based Care'),
    ('Jefferson',   'Northwest', 'Big Bend Community Based Care'),
    ('Madison',     'Northwest', 'Big Bend Community Based Care'),
    ('Taylor',      'Northwest', 'Big Bend Community Based Care'),

    ('Hamilton',    'Northeast', 'Lutheran Services Florida Health Systems'),
    ('Suwannee',    'Northeast', 'Lutheran Services Florida Health Systems'),
    ('Lafayette',   'Northeast', 'Lutheran Services Florida Health Systems'),
    ('Columbia',    'Northeast', 'Lutheran Services Florida Health Systems'),
    ('Baker',       'Northeast', 'Lutheran Services Florida Health Systems'),
    ('Union',       'Northeast', 'Lutheran Services Florida Health Systems'),
    ('Bradford',    'Northeast', 'Lutheran Services Florida Health Systems'),
    ('Alachua',     'Northeast', 'Lutheran Services Florida Health Systems'),
    ('Gilchrist',   'Northeast', 'Lutheran Services Florida Health Systems'),
    ('Dixie',       'Northeast', 'Lutheran Services Florida Health Systems'),
    ('Levy',        'Northeast', 'Lutheran Services Florida Health Systems'),
    ('Nassau',      'Northeast', 'Lutheran Services Florida Health Systems'),
    ('Duval',       'Northeast', 'Lutheran Services Florida Health Systems'),
    ('Clay',        'Northeast', 'Lutheran Services Florida Health Systems'),
    ('St. Johns',   'Northeast', 'Lutheran Services Florida Health Systems'),
    ('Putnam',      'Northeast', 'Lutheran Services Florida Health Systems'),
    ('Flagler',     'Northeast', 'Lutheran Services Florida Health Systems'),

    ('Marion',      'Central',   'Central Florida Cares Health System'),
    ('Lake',        'Central',   'Central Florida Cares Health System'),
    ('Sumter',      'Central',   'Central Florida Cares Health System'),
    ('Citrus',      'Central',   'Central Florida Cares Health System'),
    ('Hernando',    'Central',   'Central Florida Cares Health System'),
    ('Orange',      'Central',   'Central Florida Cares Health System'),
    ('Seminole',    'Central',   'Central Florida Cares Health System'),
    ('Osceola',     'Central',   'Central Florida Cares Health System'),
    ('Brevard',     'Central',   'Central Florida Cares Health System'),
    ('Volusia',     'Central',   'Central Florida Cares Health System'),

    ('Pasco',       'Suncoast',  'Central Florida Behavioral Health Network'),
    ('Pinellas',    'Suncoast',  'Central Florida Behavioral Health Network'),
    ('Hillsborough','Suncoast',  'Central Florida Behavioral Health Network'),
    ('Polk',        'Suncoast',  'Central Florida Behavioral Health Network'),
    ('Hardee',      'Suncoast',  'Central Florida Behavioral Health Network'),
    ('Highlands',   'Suncoast',  'Central Florida Behavioral Health Network'),
    ('Manatee',     'Suncoast',  'Central Florida Behavioral Health Network'),
    ('Sarasota',    'Suncoast',  'Central Florida Behavioral Health Network'),
    ('DeSoto',      'Suncoast',  'Central Florida Behavioral Health Network'),
    ('Charlotte',   'Suncoast',  'Central Florida Behavioral Health Network'),
    ('Lee',         'Suncoast',  'Central Florida Behavioral Health Network'),
    ('Collier',     'Suncoast',  'Central Florida Behavioral Health Network'),
    ('Glades',      'Suncoast',  'Central Florida Behavioral Health Network'),
    ('Hendry',      'Suncoast',  'Central Florida Behavioral Health Network'),

    ('Indian River','Southeast', 'Southeast Florida Behavioral Health Network'),
    ('Okeechobee',  'Southeast', 'Southeast Florida Behavioral Health Network'),
    ('St. Lucie',   'Southeast', 'Southeast Florida Behavioral Health Network'),
    ('Martin',      'Southeast', 'Southeast Florida Behavioral Health Network'),
    ('Palm Beach',  'Southeast', 'Southeast Florida Behavioral Health Network'),
    ('Broward',     'Southeast', 'Broward Behavioral Health Coalition'),

    ('Miami-Dade',  'Southern',  'Thriving Mind South Florida'),
    ('Monroe',      'Southern',  'Thriving Mind South Florida');


-- =====================================================================
-- INSURANCE PLANS
-- Carrier names reflect plans operating in Florida. Which providers
-- accept which plan is fictional. Verify current SMMC plan lineup
-- before presenting this as accurate.
-- =====================================================================

INSERT INTO insurance_plans (name, plan_type) VALUES
    ('Sunshine Health (Medicaid)',              'MEDICAID'),
    ('Simply Healthcare (Medicaid)',            'MEDICAID'),
    ('Humana Healthy Horizons (Medicaid)',      'MEDICAID'),
    ('Molina Healthcare (Medicaid)',            'MEDICAID'),
    ('UnitedHealthcare Community Plan',         'MEDICAID'),
    ('Aetna Better Health of Florida',          'MEDICAID'),
    ('Original Medicare',                       'MEDICARE'),
    ('Medicare Advantage',                      'MEDICARE'),
    ('Florida Blue',                            'COMMERCIAL'),
    ('Cigna Behavioral Health',                 'COMMERCIAL'),
    ('Aetna Commercial',                        'COMMERCIAL'),
    ('UnitedHealthcare / Optum',                'COMMERCIAL'),
    ('Ambetter from Sunshine Health',           'MARKETPLACE'),
    ('Florida Blue Marketplace',                'MARKETPLACE'),
    ('Sliding Scale Fee',                       'SLIDING_SCALE'),
    ('Self-Pay',                                'SELF_PAY');


-- =====================================================================
-- ORGANIZATIONS - all fictional
-- Joined to counties by name so no county ids are hardcoded.
-- =====================================================================

INSERT INTO organizations
    (name, org_type, county_id, address_line1, city, postal_code,
     phone, website, baker_act_receiving_facility)
SELECT v.name, v.org_type, c.id, v.address, v.city, v.zip,
       v.phone, v.website, v.baker_act
FROM (
    SELECT 'Example Brickell Behavioral Associates' AS name, 'GROUP_PRACTICE' AS org_type,
           'Miami-Dade' AS county, '1450 Brickell Bay Dr, Suite 310' AS address,
           'Miami' AS city, '33131' AS zip, '305-555-0148' AS phone,
           'https://example.org/bayfront' AS website, FALSE AS baker_act
    UNION ALL SELECT 'Example Coral Way Counseling Center','PRIVATE_PRACTICE','Miami-Dade','2900 SW 22nd St, Suite 105','Miami','33145','305-555-0172','https://example.org/coralway',FALSE
    UNION ALL SELECT 'Example Little Haiti Wellness Center','FQHC','Miami-Dade','5900 NE 2nd Ave','Miami','33137','305-555-0193','https://example.org/lhwc',FALSE
    UNION ALL SELECT 'Example Plantation Community Mental Health','COMMUNITY_MENTAL_HEALTH_CENTER','Broward','8200 W Sunrise Blvd','Plantation','33322','954-555-0119','https://example.org/sawgrass',TRUE
    UNION ALL SELECT 'Example Fort Lauderdale Crisis Center','CRISIS_STABILIZATION_UNIT','Broward','3100 N Federal Hwy','Fort Lauderdale','33306','954-555-0164','https://example.org/newhorizon',TRUE
    UNION ALL SELECT 'Example Boca Raton Family Therapy','GROUP_PRACTICE','Palm Beach','2255 Glades Rd, Suite 324','Boca Raton','33431','561-555-0137','https://example.org/intracoastal',FALSE
    UNION ALL SELECT 'Example Jacksonville Behavioral Health','COMMUNITY_MENTAL_HEALTH_CENTER','Duval','1201 Riverside Ave','Jacksonville','32204','904-555-0155','https://example.org/sjrbh',TRUE
    UNION ALL SELECT 'Example Jacksonville Recovery Partners','NONPROFIT','Duval','4420 Beach Blvd','Jacksonville','32207','904-555-0181','https://example.org/riverside',FALSE
    UNION ALL SELECT 'Example Ybor Psychological Services','GROUP_PRACTICE','Hillsborough','1901 E 7th Ave, Suite 200','Tampa','33605','813-555-0126','https://example.org/ybor',FALSE
    UNION ALL SELECT 'Example Clearwater Youth Counseling','PRIVATE_PRACTICE','Pinellas','600 Cleveland St, Suite 410','Clearwater','33755','727-555-0143','https://example.org/bayc',FALSE
    UNION ALL SELECT 'Example Orlando Mental Health Group','GROUP_PRACTICE','Orange','425 N Magnolia Ave','Orlando','32801','407-555-0158','https://example.org/lakeeola',FALSE
    UNION ALL SELECT 'Example Longwood Wellness Partners','PRIVATE_PRACTICE','Seminole','1155 W SR 434, Suite 220','Longwood','32750','407-555-0177','https://example.org/seminolewp',FALSE
    UNION ALL SELECT 'Example Tallahassee Counseling','GROUP_PRACTICE','Leon','1830 Thomasville Rd','Tallahassee','32303','850-555-0112','https://example.org/capitalcity',FALSE
    UNION ALL SELECT 'Example Fort Myers Behavioral Alliance','COMMUNITY_MENTAL_HEALTH_CENTER','Lee','4130 Cleveland Ave','Fort Myers','33901','239-555-0169','https://example.org/gulfcoast',TRUE
    UNION ALL SELECT 'Example Statewide Telehealth','TELEHEALTH_ONLY','Orange','800 N Orange Ave, Suite 500','Orlando','32801','888-555-0100','https://example.org/stc',FALSE
    UNION ALL SELECT 'Example Hillsborough Behavioral Hospital','HOSPITAL','Hillsborough','2600 N Rocky Point Dr','Tampa','33607','813-555-0190','https://example.org/hbh',TRUE
    UNION ALL SELECT 'Example Ocala Residential Treatment','RESIDENTIAL','Marion','3100 SW College Rd','Ocala','34474','352-555-0134','https://example.org/ortc',FALSE
) AS v
JOIN counties c ON c.name = v.county;


-- =====================================================================
-- PROVIDERS
--
-- Capacity notes for the demo:
--   * several providers have open_slots = 0 AND accepting = FALSE
--     (consistent) so the WAITLISTED path can be demonstrated
--   * no provider is accepting with zero slots - that contradiction is
--     what verification query 2 checks for
-- =====================================================================

INSERT INTO providers
    (first_name, last_name, credential, license_number, organization_id,
     bio, years_experience, offers_telehealth, offers_in_person,
     accepting_new_clients, open_slots, waitlist_count, typical_wait_days)
SELECT v.first_name, v.last_name, v.credential, v.license, o.id,
       v.bio, v.years, v.telehealth, v.in_person,
       v.accepting, v.slots, v.waitlist, v.wait_days
FROM (
    -- ---------- Miami-Dade ----------
    SELECT 'Elena' AS first_name, 'Vasquez' AS last_name, 'LMHC' AS credential,
           'FL-LMHC-10234' AS license, 'Example Brickell Behavioral Associates' AS org,
           'Bilingual counselor focused on anxiety and life transitions.' AS bio,
           9 AS years, TRUE AS telehealth, TRUE AS in_person,
           TRUE AS accepting, 4 AS slots, 2 AS waitlist, 10 AS wait_days
    UNION ALL SELECT 'Marcus','Bell','PSYCHIATRIST','FL-MD-40118','Example Brickell Behavioral Associates','Adult psychiatry with an emphasis on mood disorders and medication review.',16,TRUE,TRUE,TRUE,2,9,28
    UNION ALL SELECT 'Priya','Raman','PSYCHOLOGIST','FL-PSY-20451','Example Brickell Behavioral Associates','Assessment and evidence-based treatment for OCD and anxiety.',12,FALSE,TRUE,FALSE,0,14,45
    UNION ALL SELECT 'Sofia','Delgado','LCSW','FL-LCSW-31092','Example Coral Way Counseling Center','Trauma-informed clinical social work with adults and couples.',7,TRUE,TRUE,TRUE,6,0,7
    UNION ALL SELECT 'Andres','Pichardo','LMFT','FL-LMFT-50277','Example Coral Way Counseling Center','Family systems work with adolescents and caregivers.',11,FALSE,TRUE,TRUE,3,4,14
    UNION ALL SELECT 'Rosemarie','Joseph','LCSW','FL-LCSW-31447','Example Little Haiti Wellness Center','Haitian Creole-speaking clinician serving immigrant families.',14,TRUE,TRUE,TRUE,5,3,12
    UNION ALL SELECT 'Jean','Baptiste','CAP','FL-CAP-60310','Example Little Haiti Wellness Center','Certified addiction professional supporting recovery and co-occurring care.',8,FALSE,TRUE,TRUE,7,1,5

    -- ---------- Broward ----------
    UNION ALL SELECT 'Denise','Okafor','PSYCHIATRIC_ARNP','FL-ARNP-70225','Example Plantation Community Mental Health','Psychiatric nurse practitioner; medication management across the lifespan.',10,TRUE,TRUE,TRUE,3,6,21
    UNION ALL SELECT 'Gregory','Lindt','LMHC','FL-LMHC-10788','Example Plantation Community Mental Health','Community clinician focused on serious mental illness and stabilization.',19,FALSE,TRUE,FALSE,0,11,60
    UNION ALL SELECT 'Nadia','Pierre','LMHC','FL-LMHC-10903','Example Plantation Community Mental Health','Group and individual therapy; grief and trauma focus.',6,TRUE,TRUE,TRUE,8,0,4
    UNION ALL SELECT 'Alan','Reyes','PEER_SPECIALIST','FL-CRPS-80144','Example Fort Lauderdale Crisis Center','Certified recovery peer specialist; post-crisis navigation and support.',5,TRUE,TRUE,TRUE,10,0,2
    UNION ALL SELECT 'Hannah','Wexler','PSYCHOLOGIST','FL-PSY-20988','Example Fort Lauderdale Crisis Center','Clinical psychologist; stabilization follow-up and DBT-informed care.',13,FALSE,TRUE,FALSE,0,7,35

    -- ---------- Palm Beach ----------
    UNION ALL SELECT 'Rachel','Sterling','LMFT','FL-LMFT-50613','Example Boca Raton Family Therapy','Couples and family therapy; perinatal mental health.',15,TRUE,TRUE,TRUE,4,2,11
    UNION ALL SELECT 'Tomas','Iglesias','LMHC','FL-LMHC-11256','Example Boca Raton Family Therapy','Adolescent and young-adult counseling; ADHD and school stress.',8,TRUE,TRUE,TRUE,5,1,9

    -- ---------- Duval ----------
    UNION ALL SELECT 'Camille','Beaumont','PSYCHIATRIST','FL-MD-40592','Example Jacksonville Behavioral Health','Psychiatry for co-occurring and psychotic disorders.',21,FALSE,TRUE,FALSE,0,18,75
    UNION ALL SELECT 'Derrick','Oyelaran','LCSW','FL-LCSW-32015','Example Jacksonville Behavioral Health','Case-managed clinical care; veterans and military families.',12,TRUE,TRUE,TRUE,6,3,14
    UNION ALL SELECT 'Kayla','Brennan','REGISTERED_INTERN','FL-RMHCI-90337','Example Jacksonville Behavioral Health','Registered mental health counselor intern; supervised, reduced-fee sessions.',2,TRUE,TRUE,TRUE,12,0,3
    UNION ALL SELECT 'Simone','Gantt','CAP','FL-CAP-60781','Example Jacksonville Recovery Partners','Substance use treatment and MAT-adjacent counseling.',10,TRUE,TRUE,TRUE,5,2,8

    -- ---------- Hillsborough / Pinellas ----------
    UNION ALL SELECT 'Victor','Almeida','PSYCHOLOGIST','FL-PSY-21340','Example Ybor Psychological Services','Portuguese and Spanish speaking; trauma and chronic illness adjustment.',17,TRUE,TRUE,TRUE,3,5,18
    UNION ALL SELECT 'Leah','Kirkpatrick','LMHC','FL-LMHC-11704','Example Ybor Psychological Services','Eating disorder and body image work with adolescents and adults.',9,TRUE,TRUE,FALSE,0,9,40
    UNION ALL SELECT 'Nathan','Pruitt','LMFT','FL-LMFT-51028','Example Clearwater Youth Counseling','Child and family therapy; autism-informed practice.',11,FALSE,TRUE,TRUE,4,3,16
    UNION ALL SELECT 'Imani','Fowler','LCSW','FL-LCSW-32488','Example Clearwater Youth Counseling','School-aged children and caregiver support.',6,TRUE,TRUE,TRUE,7,0,6

    -- ---------- Orange / Seminole ----------
    UNION ALL SELECT 'Grace','Nakamura','PSYCHIATRIC_ARNP','FL-ARNP-70841','Example Orlando Mental Health Group','Medication management for anxiety, depression and ADHD.',9,TRUE,TRUE,TRUE,4,4,15
    UNION ALL SELECT 'Isaiah','Trent','LMHC','FL-LMHC-12190','Example Orlando Mental Health Group','Adult counseling; anger management and life transitions.',7,TRUE,TRUE,TRUE,9,0,5
    UNION ALL SELECT 'Bethany','Alcott','LCSW','FL-LCSW-32901','Example Longwood Wellness Partners','Older adult care, grief and caregiver burnout.',18,TRUE,TRUE,TRUE,3,2,12
    UNION ALL SELECT 'Owen','Radley','REGISTERED_INTERN','FL-RCSWI-90612','Example Longwood Wellness Partners','Registered clinical social work intern; sliding scale availability.',1,TRUE,TRUE,TRUE,14,0,2

    -- ---------- Leon ----------
    UNION ALL SELECT 'Tanya','Whitlock','PSYCHOLOGIST','FL-PSY-21755','Example Tallahassee Counseling','Psychological assessment and adult therapy.',14,TRUE,TRUE,TRUE,2,6,22
    UNION ALL SELECT 'Emmett','Boyd','LMHC','FL-LMHC-12633','Example Tallahassee Counseling','College-age and young-adult mental health.',5,TRUE,TRUE,TRUE,8,1,7

    -- ---------- Lee ----------
    UNION ALL SELECT 'Carmen','Solis','LMHC','FL-LMHC-13047','Example Fort Myers Behavioral Alliance','Bilingual community counseling; trauma and family stress.',13,TRUE,TRUE,TRUE,5,3,13
    UNION ALL SELECT 'Wendell','Frayne','PSYCHIATRIST','FL-MD-41077','Example Fort Myers Behavioral Alliance','Community psychiatry; bipolar and psychotic disorders.',23,FALSE,TRUE,FALSE,0,16,66

    -- ---------- Telehealth-only, statewide ----------
    UNION ALL SELECT 'Renata','Oliveira','LMHC','FL-LMHC-13512','Example Statewide Telehealth','Telehealth-only practice serving rural counties statewide.',10,TRUE,FALSE,TRUE,11,0,4
    UNION ALL SELECT 'Julian','Cassidy','PSYCHIATRIC_ARNP','FL-ARNP-71260','Example Statewide Telehealth','Telepsychiatry medication management, statewide coverage.',8,TRUE,FALSE,TRUE,6,5,19

    -- ---------- Hospital / residential ----------
    UNION ALL SELECT 'Lydia','Alvarez','PSYCHIATRIST','FL-MD-41533','Example Hillsborough Behavioral Hospital','Inpatient and step-down psychiatry; acute stabilization follow-up.',19,FALSE,TRUE,FALSE,0,12,50
    UNION ALL SELECT 'Harold','Quinn','PSYCHIATRIC_ARNP','FL-ARNP-71688','Example Hillsborough Behavioral Hospital','Outpatient medication management attached to the hospital clinic.',11,TRUE,TRUE,TRUE,3,4,17
    UNION ALL SELECT 'Felicia','Monroe','CAP','FL-CAP-61204','Example Ocala Residential Treatment','Residential substance use program; intake and aftercare planning.',12,FALSE,TRUE,TRUE,4,6,24
) AS v
JOIN organizations o ON o.name = v.org;


-- =====================================================================
-- PROVIDER INSURANCE (the many-to-many)
-- Every provider takes Self-Pay; the rest varies. Community and FQHC
-- clinicians carry the Medicaid plans, private practice skews
-- commercial - which is exactly the access gap the app surfaces.
-- =====================================================================

INSERT INTO provider_insurance (provider_id, insurance_plan_id)
SELECT p.id, ip.id
FROM providers p
JOIN insurance_plans ip ON ip.name = 'Self-Pay';

INSERT INTO provider_insurance (provider_id, insurance_plan_id)
SELECT p.id, ip.id
FROM providers p
JOIN insurance_plans ip ON (p.license_number, ip.name) IN (
    ('FL-LMHC-10234','Florida Blue'),
    ('FL-LMHC-10234','Cigna Behavioral Health'),
    ('FL-MD-40118','Florida Blue'),
    ('FL-MD-40118','Medicare Advantage'),
    ('FL-MD-40118','UnitedHealthcare / Optum'),
    ('FL-PSY-20451','Florida Blue'),
    ('FL-PSY-20451','Aetna Commercial'),
    ('FL-LCSW-31092','Sunshine Health (Medicaid)'),
    ('FL-LCSW-31092','Simply Healthcare (Medicaid)'),
    ('FL-LCSW-31092','Florida Blue'),
    ('FL-LMFT-50277','Sliding Scale Fee'),
    ('FL-LMFT-50277','Ambetter from Sunshine Health'),
    ('FL-LCSW-31447','Sunshine Health (Medicaid)'),
    ('FL-LCSW-31447','Humana Healthy Horizons (Medicaid)'),
    ('FL-LCSW-31447','Sliding Scale Fee'),
    ('FL-CAP-60310','Sunshine Health (Medicaid)'),
    ('FL-CAP-60310','Sliding Scale Fee'),

    ('FL-ARNP-70225','Sunshine Health (Medicaid)'),
    ('FL-ARNP-70225','Molina Healthcare (Medicaid)'),
    ('FL-ARNP-70225','Medicare Advantage'),
    ('FL-LMHC-10788','Sunshine Health (Medicaid)'),
    ('FL-LMHC-10788','UnitedHealthcare Community Plan'),
    ('FL-LMHC-10903','Simply Healthcare (Medicaid)'),
    ('FL-LMHC-10903','Sliding Scale Fee'),
    ('FL-CRPS-80144','Sunshine Health (Medicaid)'),
    ('FL-CRPS-80144','Sliding Scale Fee'),
    ('FL-PSY-20988','Florida Blue'),
    ('FL-PSY-20988','Aetna Commercial'),

    ('FL-LMFT-50613','Florida Blue'),
    ('FL-LMFT-50613','Cigna Behavioral Health'),
    ('FL-LMHC-11256','Florida Blue Marketplace'),
    ('FL-LMHC-11256','Aetna Commercial'),

    ('FL-MD-40592','Sunshine Health (Medicaid)'),
    ('FL-MD-40592','Aetna Better Health of Florida'),
    ('FL-MD-40592','Original Medicare'),
    ('FL-LCSW-32015','Sunshine Health (Medicaid)'),
    ('FL-LCSW-32015','UnitedHealthcare Community Plan'),
    ('FL-LCSW-32015','Original Medicare'),
    ('FL-RMHCI-90337','Sliding Scale Fee'),
    ('FL-CAP-60781','Humana Healthy Horizons (Medicaid)'),
    ('FL-CAP-60781','Sliding Scale Fee'),

    ('FL-PSY-21340','Florida Blue'),
    ('FL-PSY-21340','UnitedHealthcare / Optum'),
    ('FL-PSY-21340','Medicare Advantage'),
    ('FL-LMHC-11704','Florida Blue'),
    ('FL-LMHC-11704','Cigna Behavioral Health'),
    ('FL-LMFT-51028','Sunshine Health (Medicaid)'),
    ('FL-LMFT-51028','Simply Healthcare (Medicaid)'),
    ('FL-LCSW-32488','Sunshine Health (Medicaid)'),
    ('FL-LCSW-32488','Ambetter from Sunshine Health'),
    ('FL-LCSW-32488','Sliding Scale Fee'),

    ('FL-ARNP-70841','Florida Blue'),
    ('FL-ARNP-70841','Aetna Commercial'),
    ('FL-ARNP-70841','UnitedHealthcare / Optum'),
    ('FL-LMHC-12190','Florida Blue Marketplace'),
    ('FL-LMHC-12190','Sliding Scale Fee'),
    ('FL-LCSW-32901','Original Medicare'),
    ('FL-LCSW-32901','Medicare Advantage'),
    ('FL-LCSW-32901','Florida Blue'),
    ('FL-RCSWI-90612','Sliding Scale Fee'),

    ('FL-PSY-21755','Florida Blue'),
    ('FL-PSY-21755','Cigna Behavioral Health'),
    ('FL-LMHC-12633','Florida Blue Marketplace'),
    ('FL-LMHC-12633','Sliding Scale Fee'),

    ('FL-LMHC-13047','Sunshine Health (Medicaid)'),
    ('FL-LMHC-13047','Molina Healthcare (Medicaid)'),
    ('FL-LMHC-13047','Sliding Scale Fee'),
    ('FL-MD-41077','Sunshine Health (Medicaid)'),
    ('FL-MD-41077','Original Medicare'),

    ('FL-LMHC-13512','Sunshine Health (Medicaid)'),
    ('FL-LMHC-13512','Florida Blue'),
    ('FL-LMHC-13512','Ambetter from Sunshine Health'),
    ('FL-ARNP-71260','Sunshine Health (Medicaid)'),
    ('FL-ARNP-71260','Humana Healthy Horizons (Medicaid)'),
    ('FL-ARNP-71260','Medicare Advantage'),

    ('FL-MD-41533','Sunshine Health (Medicaid)'),
    ('FL-MD-41533','Original Medicare'),
    ('FL-MD-41533','Florida Blue'),
    ('FL-ARNP-71688','Florida Blue'),
    ('FL-ARNP-71688','Aetna Commercial'),
    ('FL-ARNP-71688','Medicare Advantage'),
    ('FL-CAP-61204','Humana Healthy Horizons (Medicaid)'),
    ('FL-CAP-61204','Sliding Scale Fee')
);


-- =====================================================================
-- SAVED PROVIDERS (shortlists)
-- =====================================================================

INSERT INTO saved_providers (user_id, provider_id, note)
SELECT u.id, p.id, v.note
FROM (
    SELECT 'alicia.moreno@example.com' AS email, 'FL-LMHC-10234' AS license, 'Speaks Spanish, close to work' AS note
    UNION ALL SELECT 'alicia.moreno@example.com','FL-LCSW-31092','Takes my plan'
    UNION ALL SELECT 'alicia.moreno@example.com','FL-LMFT-50277','Backup option'
    UNION ALL SELECT 'devon.carter@example.com','FL-LCSW-32015','Veteran experience'
    UNION ALL SELECT 'devon.carter@example.com','FL-RMHCI-90337','Short wait time'
    UNION ALL SELECT 'rosalie.jean@example.com','FL-LCSW-31447','Creole speaking'
    UNION ALL SELECT 'rosalie.jean@example.com','FL-LMHC-10903','Also Creole, group option'
    UNION ALL SELECT 'tyler.brandt@example.com','FL-LMHC-13512','Telehealth, flexible hours'
    UNION ALL SELECT 'tyler.brandt@example.com','FL-PSY-21340','Sliding scale question'
) AS v
JOIN users u     ON u.email = v.email
JOIN providers p ON p.license_number = v.license;


-- =====================================================================
-- REFERRAL REQUESTS
-- A spread of statuses so the admin queue, the accepted path and the
-- waitlisted path all have something to show.
-- =====================================================================

INSERT INTO referral_requests
    (user_id, provider_id, status, message, preferred_contact,
     submitted_at, resolved_at, resolved_by)
SELECT u.id, p.id, v.status, v.message, v.contact,
       v.submitted,
       v.resolved,
       ru.id
FROM (
    SELECT 'alicia.moreno@example.com' AS email, 'FL-LMHC-10234' AS license,
           'ACCEPTED' AS status,
           'Looking for a Spanish-speaking counselor, weekday evenings if possible.' AS message,
           'EMAIL' AS contact,
           '2026-09-08 09:15:00' AS submitted,
           '2026-09-09 14:20:00' AS resolved,
           'navigator@example.org' AS resolver

    UNION ALL SELECT 'alicia.moreno@example.com','FL-PSY-20451','WAITLISTED',
           'Interested in OCD-focused treatment.','EMAIL',
           '2026-09-10 11:40:00','2026-09-11 10:05:00','intake@example.org'

    UNION ALL SELECT 'devon.carter@example.com','FL-LCSW-32015','ACCEPTED',
           'Prior service member, would like someone familiar with that.','PHONE',
           '2026-09-12 16:02:00','2026-09-14 09:30:00','navigator@example.org'

    UNION ALL SELECT 'devon.carter@example.com','FL-MD-40592','WAITLISTED',
           'Asking about a medication consultation.','PHONE',
           '2026-09-15 08:22:00','2026-09-16 13:45:00','intake@example.org'

    UNION ALL SELECT 'rosalie.jean@example.com','FL-LCSW-31447','ACCEPTED',
           'Would prefer to speak Creole during sessions.','TEXT',
           '2026-09-16 10:10:00','2026-09-17 11:00:00','navigator@example.org'

    UNION ALL SELECT 'rosalie.jean@example.com','FL-LMHC-10788','DECLINED',
           'Following up on a referral from my primary care office.','TEXT',
           '2026-09-18 14:33:00','2026-09-19 09:12:00','intake@example.org'

    UNION ALL SELECT 'tyler.brandt@example.com','FL-LMHC-13512','PENDING',
           'Telehealth only please, I work irregular hours.','EMAIL',
           '2026-09-24 19:48:00',NULL,NULL

    UNION ALL SELECT 'tyler.brandt@example.com','FL-LMHC-12190','PENDING',
           'Interested in the anger management focus listed on the profile.','EMAIL',
           '2026-09-25 08:05:00',NULL,NULL

    UNION ALL SELECT 'alicia.moreno@example.com','FL-LMFT-50277','PENDING',
           'Asking about family sessions that include my teenager.','EMAIL',
           '2026-09-26 12:27:00',NULL,NULL

    UNION ALL SELECT 'devon.carter@example.com','FL-RMHCI-90337','WITHDRAWN',
           'Wanted to ask about reduced-fee sessions.','PHONE',
           '2026-09-20 15:14:00','2026-09-22 08:40:00',NULL
) AS v
JOIN users u          ON u.email = v.email
JOIN providers p      ON p.license_number = v.license
LEFT JOIN users ru    ON ru.email = v.resolver;


-- =====================================================================
-- REFERRAL STATUS HISTORY
--
-- Generated FROM referral_requests so the trail always reconciles,
-- rather than hand-typed and drifting out of sync.
--   step 1: every request gets a creation row (NULL -> PENDING)
--   step 2: every resolved request gets a second row (PENDING -> status)
-- The latest row's to_status therefore always equals the current
-- status - which is what verification query 1 checks.
-- =====================================================================

-- Step 1: creation rows.
INSERT INTO referral_status_history
    (referral_request_id, from_status, to_status, note, changed_by, created_at)
SELECT r.id, NULL, 'PENDING', 'Request submitted by client',
       r.user_id, r.submitted_at
FROM referral_requests r;

-- Step 2: resolution rows, for anything that left PENDING.
INSERT INTO referral_status_history
    (referral_request_id, from_status, to_status, note, changed_by, created_at)
SELECT r.id, 'PENDING', r.status,
       CASE r.status
           WHEN 'ACCEPTED'   THEN 'Open slot confirmed; intake scheduled'
           WHEN 'WAITLISTED' THEN 'No open slots; added to provider waitlist'
           WHEN 'DECLINED'   THEN 'Provider not accepting this referral at this time'
           WHEN 'WITHDRAWN'  THEN 'Withdrawn by client'
           ELSE 'Status updated'
       END,
       -- A withdrawal is the client's own action; everything else is staff.
       CASE WHEN r.status = 'WITHDRAWN' THEN r.user_id ELSE r.resolved_by END,
       r.resolved_at
FROM referral_requests r
WHERE r.status <> 'PENDING';


-- =====================================================================
-- VERIFICATION
-- Queries 1 and 2 should each return ZERO rows.
-- =====================================================================

-- 1. Current status matches the latest history row, and every request
--    has at least one history row.
-- SELECT r.id, r.status, h.to_status AS latest_history_status
-- FROM referral_requests r
-- LEFT JOIN referral_status_history h
--        ON h.id = (SELECT MAX(h2.id)
--                   FROM referral_status_history h2
--                   WHERE h2.referral_request_id = r.id)
-- WHERE h.id IS NULL OR h.to_status <> r.status;

-- 2. No provider claims to be accepting clients with zero open slots.
-- SELECT id, first_name, last_name, open_slots
-- FROM providers
-- WHERE accepting_new_clients = TRUE AND open_slots = 0 AND is_active = TRUE;

-- 3. Row counts - expect 67 / 17 / 35 / 10.
-- SELECT
--   (SELECT COUNT(*) FROM counties)           AS counties,
--   (SELECT COUNT(*) FROM organizations)      AS organizations,
--   (SELECT COUNT(*) FROM providers)          AS providers,
--   (SELECT COUNT(*) FROM referral_requests)  AS referrals;

-- 4. The flagship search: Medicaid, accepting, in one county.
-- SELECT DISTINCT p.id, p.first_name, p.last_name, p.credential,
--        o.name AS organization, c.name AS county, p.open_slots
-- FROM providers p
-- JOIN organizations o       ON o.id  = p.organization_id
-- JOIN counties c            ON c.id  = o.county_id
-- JOIN provider_insurance pi ON pi.provider_id = p.id
-- JOIN insurance_plans ip    ON ip.id = pi.insurance_plan_id
-- WHERE ip.plan_type = 'MEDICAID'
--   AND c.name = 'Miami-Dade'
--   AND p.accepting_new_clients = TRUE
--   AND p.is_active = TRUE;

-- 5. Access-gap report: how many accepting providers take each plan
--    type. A good slide - it shows the Medicaid/commercial split.
-- SELECT ip.plan_type, COUNT(DISTINCT p.id) AS accepting_providers
-- FROM insurance_plans ip
-- JOIN provider_insurance pi ON pi.insurance_plan_id = ip.id
-- JOIN providers p           ON p.id = pi.provider_id
-- WHERE p.accepting_new_clients = TRUE AND p.is_active = TRUE
-- GROUP BY ip.plan_type
-- ORDER BY accepting_providers DESC;

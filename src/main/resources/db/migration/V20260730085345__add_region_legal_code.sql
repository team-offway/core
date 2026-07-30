-- region 에 법정 시군구코드(행정표준코드 5자리)를 추가한다.
-- 관광빅데이터(locgoRegnVisitrDDList) 랭킹 매칭 키. 기존엔 시군구 '지명' 으로 매칭해 동명 지역이
-- 섞였다 — 전국에 동구 6곳·중구 6곳·서구 5곳·남구 4곳·북구 4곳·고성군 2곳이 있어, 우리 89곳 중
-- 부산 동구·부산 서구·대구 남구·대구 서구·강원 고성군·경남 고성군의 방문자수가 오염됐다.
--
-- 값의 정본은 관광빅데이터 응답의 signguCode 자체다(2026-06-15 실호출, 268개 시군구).
-- 매칭 상대에서 그대로 따왔으므로 89곳 전부 매칭이 보장된다.
-- MySQL / H2(MODE=MySQL) 호환. FK 없음.

ALTER TABLE region ADD COLUMN legal_code VARCHAR(5);

UPDATE region SET legal_code='26170' WHERE sido='부산광역시' AND sigungu='동구';
UPDATE region SET legal_code='26140' WHERE sido='부산광역시' AND sigungu='서구';
UPDATE region SET legal_code='26200' WHERE sido='부산광역시' AND sigungu='영도구';
UPDATE region SET legal_code='27200' WHERE sido='대구광역시' AND sigungu='남구';
UPDATE region SET legal_code='27170' WHERE sido='대구광역시' AND sigungu='서구';
UPDATE region SET legal_code='27720' WHERE sido='대구광역시' AND sigungu='군위군';
UPDATE region SET legal_code='28710' WHERE sido='인천광역시' AND sigungu='강화군';
UPDATE region SET legal_code='28720' WHERE sido='인천광역시' AND sigungu='옹진군';
UPDATE region SET legal_code='41820' WHERE sido='경기도' AND sigungu='가평군';
UPDATE region SET legal_code='41800' WHERE sido='경기도' AND sigungu='연천군';
UPDATE region SET legal_code='51820' WHERE sido='강원특별자치도' AND sigungu='고성군';
UPDATE region SET legal_code='51230' WHERE sido='강원특별자치도' AND sigungu='삼척시';
UPDATE region SET legal_code='51800' WHERE sido='강원특별자치도' AND sigungu='양구군';
UPDATE region SET legal_code='51830' WHERE sido='강원특별자치도' AND sigungu='양양군';
UPDATE region SET legal_code='51750' WHERE sido='강원특별자치도' AND sigungu='영월군';
UPDATE region SET legal_code='51770' WHERE sido='강원특별자치도' AND sigungu='정선군';
UPDATE region SET legal_code='51780' WHERE sido='강원특별자치도' AND sigungu='철원군';
UPDATE region SET legal_code='51190' WHERE sido='강원특별자치도' AND sigungu='태백시';
UPDATE region SET legal_code='51760' WHERE sido='강원특별자치도' AND sigungu='평창군';
UPDATE region SET legal_code='51720' WHERE sido='강원특별자치도' AND sigungu='홍천군';
UPDATE region SET legal_code='51790' WHERE sido='강원특별자치도' AND sigungu='화천군';
UPDATE region SET legal_code='51730' WHERE sido='강원특별자치도' AND sigungu='횡성군';
UPDATE region SET legal_code='43760' WHERE sido='충청북도' AND sigungu='괴산군';
UPDATE region SET legal_code='43800' WHERE sido='충청북도' AND sigungu='단양군';
UPDATE region SET legal_code='43720' WHERE sido='충청북도' AND sigungu='보은군';
UPDATE region SET legal_code='43740' WHERE sido='충청북도' AND sigungu='영동군';
UPDATE region SET legal_code='43730' WHERE sido='충청북도' AND sigungu='옥천군';
UPDATE region SET legal_code='43150' WHERE sido='충청북도' AND sigungu='제천시';
UPDATE region SET legal_code='44150' WHERE sido='충청남도' AND sigungu='공주시';
UPDATE region SET legal_code='44710' WHERE sido='충청남도' AND sigungu='금산군';
UPDATE region SET legal_code='44230' WHERE sido='충청남도' AND sigungu='논산시';
UPDATE region SET legal_code='44180' WHERE sido='충청남도' AND sigungu='보령시';
UPDATE region SET legal_code='44760' WHERE sido='충청남도' AND sigungu='부여군';
UPDATE region SET legal_code='44770' WHERE sido='충청남도' AND sigungu='서천군';
UPDATE region SET legal_code='44810' WHERE sido='충청남도' AND sigungu='예산군';
UPDATE region SET legal_code='44790' WHERE sido='충청남도' AND sigungu='청양군';
UPDATE region SET legal_code='44825' WHERE sido='충청남도' AND sigungu='태안군';
UPDATE region SET legal_code='52790' WHERE sido='전북특별자치도' AND sigungu='고창군';
UPDATE region SET legal_code='52210' WHERE sido='전북특별자치도' AND sigungu='김제시';
UPDATE region SET legal_code='52190' WHERE sido='전북특별자치도' AND sigungu='남원시';
UPDATE region SET legal_code='52730' WHERE sido='전북특별자치도' AND sigungu='무주군';
UPDATE region SET legal_code='52800' WHERE sido='전북특별자치도' AND sigungu='부안군';
UPDATE region SET legal_code='52770' WHERE sido='전북특별자치도' AND sigungu='순창군';
UPDATE region SET legal_code='52750' WHERE sido='전북특별자치도' AND sigungu='임실군';
UPDATE region SET legal_code='52740' WHERE sido='전북특별자치도' AND sigungu='장수군';
UPDATE region SET legal_code='52180' WHERE sido='전북특별자치도' AND sigungu='정읍시';
UPDATE region SET legal_code='52720' WHERE sido='전북특별자치도' AND sigungu='진안군';
UPDATE region SET legal_code='46810' WHERE sido='전라남도' AND sigungu='강진군';
UPDATE region SET legal_code='46770' WHERE sido='전라남도' AND sigungu='고흥군';
UPDATE region SET legal_code='46720' WHERE sido='전라남도' AND sigungu='곡성군';
UPDATE region SET legal_code='46730' WHERE sido='전라남도' AND sigungu='구례군';
UPDATE region SET legal_code='46710' WHERE sido='전라남도' AND sigungu='담양군';
UPDATE region SET legal_code='46780' WHERE sido='전라남도' AND sigungu='보성군';
UPDATE region SET legal_code='46910' WHERE sido='전라남도' AND sigungu='신안군';
UPDATE region SET legal_code='46870' WHERE sido='전라남도' AND sigungu='영광군';
UPDATE region SET legal_code='46830' WHERE sido='전라남도' AND sigungu='영암군';
UPDATE region SET legal_code='46890' WHERE sido='전라남도' AND sigungu='완도군';
UPDATE region SET legal_code='46880' WHERE sido='전라남도' AND sigungu='장성군';
UPDATE region SET legal_code='46800' WHERE sido='전라남도' AND sigungu='장흥군';
UPDATE region SET legal_code='46900' WHERE sido='전라남도' AND sigungu='진도군';
UPDATE region SET legal_code='46860' WHERE sido='전라남도' AND sigungu='함평군';
UPDATE region SET legal_code='46820' WHERE sido='전라남도' AND sigungu='해남군';
UPDATE region SET legal_code='46790' WHERE sido='전라남도' AND sigungu='화순군';
UPDATE region SET legal_code='47830' WHERE sido='경상북도' AND sigungu='고령군';
UPDATE region SET legal_code='47280' WHERE sido='경상북도' AND sigungu='문경시';
UPDATE region SET legal_code='47920' WHERE sido='경상북도' AND sigungu='봉화군';
UPDATE region SET legal_code='47250' WHERE sido='경상북도' AND sigungu='상주시';
UPDATE region SET legal_code='47840' WHERE sido='경상북도' AND sigungu='성주군';
UPDATE region SET legal_code='47170' WHERE sido='경상북도' AND sigungu='안동시';
UPDATE region SET legal_code='47770' WHERE sido='경상북도' AND sigungu='영덕군';
UPDATE region SET legal_code='47760' WHERE sido='경상북도' AND sigungu='영양군';
UPDATE region SET legal_code='47210' WHERE sido='경상북도' AND sigungu='영주시';
UPDATE region SET legal_code='47230' WHERE sido='경상북도' AND sigungu='영천시';
UPDATE region SET legal_code='47940' WHERE sido='경상북도' AND sigungu='울릉군';
UPDATE region SET legal_code='47930' WHERE sido='경상북도' AND sigungu='울진군';
UPDATE region SET legal_code='47730' WHERE sido='경상북도' AND sigungu='의성군';
UPDATE region SET legal_code='47820' WHERE sido='경상북도' AND sigungu='청도군';
UPDATE region SET legal_code='47750' WHERE sido='경상북도' AND sigungu='청송군';
UPDATE region SET legal_code='48880' WHERE sido='경상남도' AND sigungu='거창군';
UPDATE region SET legal_code='48820' WHERE sido='경상남도' AND sigungu='고성군';
UPDATE region SET legal_code='48840' WHERE sido='경상남도' AND sigungu='남해군';
UPDATE region SET legal_code='48270' WHERE sido='경상남도' AND sigungu='밀양시';
UPDATE region SET legal_code='48860' WHERE sido='경상남도' AND sigungu='산청군';
UPDATE region SET legal_code='48720' WHERE sido='경상남도' AND sigungu='의령군';
UPDATE region SET legal_code='48740' WHERE sido='경상남도' AND sigungu='창녕군';
UPDATE region SET legal_code='48850' WHERE sido='경상남도' AND sigungu='하동군';
UPDATE region SET legal_code='48730' WHERE sido='경상남도' AND sigungu='함안군';
UPDATE region SET legal_code='48870' WHERE sido='경상남도' AND sigungu='함양군';
UPDATE region SET legal_code='48890' WHERE sido='경상남도' AND sigungu='합천군';

-- 89곳 전부 채워졌으므로 NOT NULL 로 굳힌다(이후 신규 지역도 코드를 반드시 갖게).
ALTER TABLE region MODIFY legal_code VARCHAR(5) NOT NULL;

-- 랭킹 매칭이 코드로 조회한다. 시군구코드는 지역당 하나뿐이라 UNIQUE 로 중복 시드를 막는다.
CREATE UNIQUE INDEX uk_region_legal_code ON region (legal_code);

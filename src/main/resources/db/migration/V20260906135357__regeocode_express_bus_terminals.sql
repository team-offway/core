-- 고속버스 터미널 좌표를 되살린다(#463).
--
-- #436 이 시외를 재지오코딩할 때 고속은 **소재지를 얻을 길이 없어** 195곳 중 184곳을 비웠다. 실호출로
-- 확인했다 — TAGO 고속 목록(`GetExpBusTrminlList`)은 terminalId·terminalNm 뿐이고 cityCode 필터도
-- 안 먹는다(붙여도 453건 그대로). 시외 목록이 cityName 을 주는 것과 다르다.
--
-- 그 결과 89개 인구감소지역이 고속버스로 닿는 곳이 **1곳**이 됐다. 고속버스가 사실상 죽어 있었다.
--
-- **세 가지로 채운다.**
--
--   ① 쌍둥이 161곳 — 한 건물에서 고속·시외를 함께 취급하는 종합터미널이 많아 같은 곳이 두 목록에
--      다른 이름으로 올라 있다. 이미 검증한 시외 좌표를 그대로 쓴다. 외부 호출 0.
--      **괄호 안 한정어까지 맞춰야 한다** — 안 맞추면 `광주(유·스퀘어)`(광주광역시)에 `광주(경기)` 좌표가
--      붙는다. 270km 밖이다. 첫 회차에서 실제로 그랬다.
--
--   ② 지오코딩 97곳 — 이름에 지역이 실린 것이 많다(`파주문산`·`고양화정`·`용인신갈`). 그 접두어로
--      제약해 찾고, 터미널 계열인지와 이름 겹침을 확인한다. **화물 터미널을 배제한다** — `송도` 로 찾으면
--      선광신컨테이너터미널이 나온다. 역시 첫 회차에서 통과했다.
--
--   ③ 손으로 바로잡은 3곳 — 자동 규칙이 같은 좌표로 묶었으나 실제로는 다른 곳이었다.
--      기존 테스트(`이름이_다른_터미널이_같은_좌표를_쓰지_않는다`)가 잡아 줬다.
--
-- 같은 좌표에 이름 계열이 다른 것이 겹치면 **먼저 것만 남긴다**(4곳 보류). 대전복합·대전시외처럼
-- 같은 건물인 경우라, 한쪽만 좌표를 가져도 최근접 탐색은 그 자리를 찾는다.
--
-- 나머지는 비운 채로 둔다. TAGO 목록에는 `광주(500)_수수료` 처럼 터미널이 아닌 행도 섞여 있다.
-- 틀린 좌표는 resolver 가 엉뚱한 곳을 답하게 하지만, 빈 좌표는 최근접 탐색에서 빠질 뿐이다(#436·#452 와 같은 판단).

-- ── 쌍둥이: 검증된 시외 터미널 좌표를 그대로 쓴다 (161곳) ──
-- 강릉 — 시외 `강릉`(NAI2551901) 와 같은 곳
UPDATE bus_terminal SET lat = 37.75468042, lng = 128.87887571 WHERE code = 'NAEK200';
-- 강진 — 시외 `강진`(NAI5923401) 와 같은 곳
UPDATE bus_terminal SET lat = 34.63855259, lng = 126.76787003 WHERE code = 'NAEK535';
-- 경북도청 — 시외 `경북도청(신)`(NAI3684901) 와 같은 곳
UPDATE bus_terminal SET lat = 36.57059118, lng = 128.50019489 WHERE code = 'NAEK852';
-- 경주 — 시외 `경주시외`(NAI3815701) 와 같은 곳
UPDATE bus_terminal SET lat = 35.83980001, lng = 129.20246875 WHERE code = 'NAEK815';
-- 경주 — 시외 `경주시외`(NAI3815701) 와 같은 곳
UPDATE bus_terminal SET lat = 35.83980001, lng = 129.20246875 WHERE code = 'NAEK894';
-- 고창 — 시외 `고창`(NAI5643301) 와 같은 곳
UPDATE bus_terminal SET lat = 35.43800679, lng = 126.69395433 WHERE code = 'NAEK635';
-- 고흥 — 시외 `고흥`(NAI5954001) 와 같은 곳
UPDATE bus_terminal SET lat = 34.60740511, lng = 127.28106074 WHERE code = 'NAEK540';
-- 공주 — 시외 `공주`(NAI3258501) 와 같은 곳
UPDATE bus_terminal SET lat = 36.46857368, lng = 127.13475802 WHERE code = 'NAEK320';
-- 관산 — 시외 `관산`(NAI5935101) 와 같은 곳
UPDATE bus_terminal SET lat = 34.56579520, lng = 126.93662263 WHERE code = 'NAEK576';
-- 광양 — 시외 `광양`(NAI5775801) 와 같은 곳
UPDATE bus_terminal SET lat = 34.96971727, lng = 127.58998374 WHERE code = 'NAEK520';
-- 광주(유·스퀘어) — 시외 `광주(유·스퀘어)`(NAI6193701) 와 같은 곳
UPDATE bus_terminal SET lat = 35.16040761, lng = 126.87931250 WHERE code = 'NAEK500';
-- 괴산 — 시외 `괴산`(NAI2803301) 와 같은 곳
UPDATE bus_terminal SET lat = 36.80853180, lng = 127.79459248 WHERE code = 'NAEK457';
-- 구례 — 시외 `구례`(NAI5765401) 와 같은 곳
UPDATE bus_terminal SET lat = 35.20670460, lng = 127.46859623 WHERE code = 'NAEK519';
-- 구미 — 시외 `구미`(NAI3923301) 와 같은 곳
UPDATE bus_terminal SET lat = 36.12270234, lng = 128.35208925 WHERE code = 'NAEK810';
-- 구미 — 시외 `구미`(NAI3923301) 와 같은 곳
UPDATE bus_terminal SET lat = 36.12270234, lng = 128.35208925 WHERE code = 'NAEK933';
-- 구인사 — 시외 `구인사`(NAI2702001) 와 같은 곳
UPDATE bus_terminal SET lat = 37.03649765, lng = 128.48066153 WHERE code = 'NAEK463';
-- 군산 — 시외 `군산`(NAI5403701) 와 같은 곳
UPDATE bus_terminal SET lat = 35.97741721, lng = 126.72444685 WHERE code = 'NAEK610';
-- 금산 — 시외 `금산`(NAI3273501) 와 같은 곳
UPDATE bus_terminal SET lat = 36.10529496, lng = 127.49075998 WHERE code = 'NAEK330';
-- 기지시 — 시외 `기지시`(NAI3173301) 와 같은 곳
UPDATE bus_terminal SET lat = 36.90415581, lng = 126.69438325 WHERE code = 'NAEK388';
-- 김제 — 시외 `김제`(NAI5437901) 와 같은 곳
UPDATE bus_terminal SET lat = 35.80343440, lng = 126.89370144 WHERE code = 'NAEK620';
-- 김천 — 시외 `김천`(NAI3958601) 와 같은 곳
UPDATE bus_terminal SET lat = 36.12366671, lng = 128.11832599 WHERE code = 'NAEK820';
-- 김천 — 시외 `김천`(NAI3958601) 와 같은 곳
UPDATE bus_terminal SET lat = 36.12366671, lng = 128.11832599 WHERE code = 'NAEK931';
-- 김해 — 시외 `김해`(NAI5093801) 와 같은 곳
UPDATE bus_terminal SET lat = 35.22786917, lng = 128.87339504 WHERE code = 'NAEK735';
-- 김해공항 — 시외 `김해공항`(NAI4671801) 와 같은 곳
UPDATE bus_terminal SET lat = 35.17248776, lng = 128.94678530 WHERE code = 'NAEK740';
-- 나주 — 시외 `나주`(NAI5825501) 와 같은 곳
UPDATE bus_terminal SET lat = 35.03361936, lng = 126.72149985 WHERE code = 'NAEK530';
-- 남악 — 시외 `남악`(NAI5856701) 와 같은 곳
UPDATE bus_terminal SET lat = 34.81417637, lng = 126.46212526 WHERE code = 'NAEK592';
-- 남원 — 시외 `남원`(NAI5576001) 와 같은 곳
UPDATE bus_terminal SET lat = 35.40969002, lng = 127.38797301 WHERE code = 'NAEK625';
-- 남청주 — 시외 `남청주`(NAI2863501) 와 같은 곳
UPDATE bus_terminal SET lat = 36.60830782, lng = 127.47957519 WHERE code = 'NAEK402';
-- 내포 — 시외 `내포`(NAI3241601) 와 같은 곳
UPDATE bus_terminal SET lat = 36.65902489, lng = 126.67052053 WHERE code = 'NAEK390';
-- 녹동 — 시외 `녹동`(NAI5955501) 와 같은 곳
UPDATE bus_terminal SET lat = 34.53287468, lng = 127.13899947 WHERE code = 'NAEK545';
-- 논산 — 시외 `논산`(NAI3295401) 와 같은 곳
UPDATE bus_terminal SET lat = 36.20312149, lng = 127.08823564 WHERE code = 'NAEK370';
-- 능주 — 시외 `능주`(NAI5815301) 와 같은 곳
UPDATE bus_terminal SET lat = 34.99154603, lng = 126.95803393 WHERE code = 'NAEK587';
-- 단양 — 시외 `단양`(NAI2701101) 와 같은 곳
UPDATE bus_terminal SET lat = 36.98553151, lng = 128.37080268 WHERE code = 'NAEK460';
-- 담양 — 시외 `담양`(NAI5734401) 와 같은 곳
UPDATE bus_terminal SET lat = 35.31522179, lng = 126.98383821 WHERE code = 'NAEK582';
-- 당진 — 시외 `당진`(NAI3177101) 와 같은 곳
UPDATE bus_terminal SET lat = 36.90245620, lng = 126.64596403 WHERE code = 'NAEK312';
-- 대구서부 — 시외 `대구서부`(NAI4248201) 와 같은 곳
UPDATE bus_terminal SET lat = 35.83672828, lng = 128.55782718 WHERE code = 'NAEK811';
-- 대전복합 — 시외 `대전복합`(NAI3455101) 와 같은 곳
UPDATE bus_terminal SET lat = 36.35032438, lng = 127.43674997 WHERE code = 'NAEK300';
-- 대전복합 — 시외 `대전복합`(NAI3455101) 와 같은 곳
UPDATE bus_terminal SET lat = 36.35032438, lng = 127.43674997 WHERE code = 'NAEK301';
-- 마산 — 시외 `마산`(NAI5135601) 와 같은 곳
UPDATE bus_terminal SET lat = 35.23906692, lng = 128.58346365 WHERE code = 'NAEK705';
-- 목포 — 시외 `목포`(NAI5864201) 와 같은 곳
UPDATE bus_terminal SET lat = 34.81274206, lng = 126.41782065 WHERE code = 'NAEK505';
-- 무안 — 시외 `무안`(NAI5852401) 와 같은 곳
UPDATE bus_terminal SET lat = 34.98811734, lng = 126.47822059 WHERE code = 'NAEK550';
-- 무주 — 시외 `무주`(NAI5551501) 와 같은 곳
UPDATE bus_terminal SET lat = 36.00468271, lng = 127.66438191 WHERE code = 'NAEK655';
-- 문장 — 시외 `문장`(NAI5711701) 와 같은 곳
UPDATE bus_terminal SET lat = 35.18118512, lng = 126.60561545 WHERE code = 'NAEK584';
-- 벌교 — 시외 `벌교`(NAI5942301) 와 같은 곳
UPDATE bus_terminal SET lat = 34.84828266, lng = 127.35109351 WHERE code = 'NAEK555';
-- 보령 — 시외 `보령`(NAI3345801) 와 같은 곳
UPDATE bus_terminal SET lat = 36.34238301, lng = 126.58962744 WHERE code = 'NAEK395';
-- 보성 — 시외 `보성`(NAI5945801) 와 같은 곳
UPDATE bus_terminal SET lat = 34.76391237, lng = 127.07561693 WHERE code = 'NAEK554';
-- 보은 — 시외 `보은`(NAI2891101) 와 같은 곳
UPDATE bus_terminal SET lat = 36.48314912, lng = 127.72177465 WHERE code = 'NAEK409';
-- 부안 — 시외 `부안`(NAI5630801) 와 같은 곳
UPDATE bus_terminal SET lat = 35.72664562, lng = 126.73701372 WHERE code = 'NAEK640';
-- 부여 — 시외 `부여`(NAI3315201) 와 같은 곳
UPDATE bus_terminal SET lat = 36.28039264, lng = 126.91030620 WHERE code = 'NAEK372';
-- 부천 — 시외 `부천`(NAI1454501) 와 같은 곳
UPDATE bus_terminal SET lat = 37.50363259, lng = 126.75671001 WHERE code = 'NAEK101';
-- 삼척 — 시외 `삼척`(NAI2592901) 와 같은 곳
UPDATE bus_terminal SET lat = 37.44014690, lng = 129.16903789 WHERE code = 'NAEK220';
-- 상주 — 시외 `상주`(NAI3718101) 와 같은 곳
UPDATE bus_terminal SET lat = 36.41907395, lng = 128.15143898 WHERE code = 'NAEK825';
-- 서산 — 시외 `서산`(NAI3198101) 와 같은 곳
UPDATE bus_terminal SET lat = 36.78184662, lng = 126.45854902 WHERE code = 'NAEK313';
-- 서산 — 시외 `서산`(NAI3198101) 와 같은 곳
UPDATE bus_terminal SET lat = 36.78184662, lng = 126.45854902 WHERE code = 'NAEK393';
-- 서수원 — 시외 `서수원`(NAI1640501) 와 같은 곳
UPDATE bus_terminal SET lat = 37.28253344, lng = 126.97076045 WHERE code = 'NAEK109';
-- 성남 — 시외 `성남`(NAI1349701) 와 같은 곳
UPDATE bus_terminal SET lat = 37.41312486, lng = 127.12741533 WHERE code = 'NAEK120';
-- 성남 — 시외 `성남`(NAI1349701) 와 같은 곳
UPDATE bus_terminal SET lat = 37.41312486, lng = 127.12741533 WHERE code = 'NAEK121';
-- 성연 — 시외 `성연`(NAI3193101) 와 같은 곳
UPDATE bus_terminal SET lat = 36.83900159, lng = 126.46147690 WHERE code = 'NAEK386';
-- 세종청사 — 시외 `세종청사`(NAI3010701) 와 같은 곳
UPDATE bus_terminal SET lat = 36.50419693, lng = 127.26149071 WHERE code = 'NAEK353';
-- 속리산 — 시외 `속리산`(NAI2890801) 와 같은 곳
UPDATE bus_terminal SET lat = 36.52784826, lng = 127.81965472 WHERE code = 'NAEK408';
-- 속초 — 시외 `속초`(NAI2482701) 와 같은 곳
UPDATE bus_terminal SET lat = 38.21122269, lng = 128.59081355 WHERE code = 'NAEK230';
-- 송광사 — 시외 `송광사`(NAI5791301) 와 같은 곳
UPDATE bus_terminal SET lat = 35.00992758, lng = 127.25555109 WHERE code = 'NAEK556';
-- 수원 — 시외 `수원터미널`(NAI1658501) 와 같은 곳
UPDATE bus_terminal SET lat = 37.25108002, lng = 127.01982909 WHERE code = 'NAEK110';
-- 순창 — 시외 `순창`(NAI5603501) 와 같은 곳
UPDATE bus_terminal SET lat = 35.37680937, lng = 127.14130868 WHERE code = 'NAEK645';
-- 순천 — 시외 `순천`(NAI5796001) 와 같은 곳
UPDATE bus_terminal SET lat = 34.94759306, lng = 127.49136191 WHERE code = 'NAEK515';
-- 신탄진역 — 시외 `신탄진역`(NAI3431101) 와 같은 곳
UPDATE bus_terminal SET lat = 36.44937136, lng = 127.42933580 WHERE code = 'NAEK303';
-- 아산 — 시외 `아산(온양)`(NAI3151704) 와 같은 곳
UPDATE bus_terminal SET lat = 36.78446199, lng = 127.01535027 WHERE code = 'NAEK345';
-- 아산시외 — 시외 `아산(온양)`(NAI3151704) 와 같은 곳
UPDATE bus_terminal SET lat = 36.78446199, lng = 127.01535027 WHERE code = 'NAEK336';
-- 안동 — 시외 `안동`(NAI3663601) 와 같은 곳
UPDATE bus_terminal SET lat = 36.57437126, lng = 128.67612823 WHERE code = 'NAEK840';
-- 안동(경) — 시외 `안동`(NAI3663601) 와 같은 곳
UPDATE bus_terminal SET lat = 36.57437126, lng = 128.67612823 WHERE code = 'NAEK841';
-- 안면도 — 시외 `안면도`(NAI3216401) 와 같은 곳
UPDATE bus_terminal SET lat = 36.51830026, lng = 126.34563787 WHERE code = 'NAEK396';
-- 안산 — 시외 `안산터미널`(NAI1529901) 와 같은 곳
UPDATE bus_terminal SET lat = 37.31684779, lng = 126.84626978 WHERE code = 'NAEK190';
-- 안성 — 시외 `안성`(NAI1758501) 와 같은 곳
UPDATE bus_terminal SET lat = 37.01223615, lng = 127.29360820 WHERE code = 'NAEK130';
-- 안양역 — 시외 `안양역`(NAI1399201) 와 같은 곳
UPDATE bus_terminal SET lat = 37.40113627, lng = 126.92170148 WHERE code = 'NAEK135';
-- 안중 — 시외 `안중`(NAI1794301) 와 같은 곳
UPDATE bus_terminal SET lat = 36.97767743, lng = 126.92334932 WHERE code = 'NAEK177';
-- 양산 — 시외 `양산`(NAI5062901) 와 같은 곳
UPDATE bus_terminal SET lat = 35.33564641, lng = 129.02676168 WHERE code = 'NAEK745';
-- 양산 — 시외 `양산`(NAI5062901) 와 같은 곳
UPDATE bus_terminal SET lat = 35.33564641, lng = 129.02676168 WHERE code = 'NAEK888';
-- 양양 — 시외 `양양`(NAI2503101) 와 같은 곳
UPDATE bus_terminal SET lat = 38.08529403, lng = 128.62977670 WHERE code = 'NAEK270';
-- 여수 — 시외 `여수`(NAI5971501) 와 같은 곳
UPDATE bus_terminal SET lat = 34.75820302, lng = 127.71699693 WHERE code = 'NAEK510';
-- 여주 — 시외 `여주`(NAI1263101) 와 같은 곳
UPDATE bus_terminal SET lat = 37.29017003, lng = 127.63520963 WHERE code = 'NAEK140';
-- 여천 — 시외 `여천`(NAI5963501) 와 같은 곳
UPDATE bus_terminal SET lat = 34.77768385, lng = 127.65141473 WHERE code = 'NAEK509';
-- 영광 — 시외 `영광`(NAI5704301) 와 같은 곳
UPDATE bus_terminal SET lat = 35.27587406, lng = 126.50205377 WHERE code = 'NAEK560';
-- 영덕 — 시외 `영덕`(NAI3643101) 와 같은 곳
UPDATE bus_terminal SET lat = 36.41449104, lng = 129.37264184 WHERE code = 'NAEK843';
-- 영월 — 시외 `영월`(NAI2623601) 와 같은 곳
UPDATE bus_terminal SET lat = 37.18324208, lng = 128.46563099 WHERE code = 'NAEK272';
-- 영주 — 시외 `영주`(NAI3607801) 와 같은 곳
UPDATE bus_terminal SET lat = 36.82709934, lng = 128.60576011 WHERE code = 'NAEK835';
-- 영천 — 시외 `영천`(NAI3888501) 와 같은 곳
UPDATE bus_terminal SET lat = 35.96066762, lng = 128.92753286 WHERE code = 'NAEK845';
-- 영천 — 시외 `영천`(NAI3888501) 와 같은 곳
UPDATE bus_terminal SET lat = 35.96066762, lng = 128.92753286 WHERE code = 'NAEK896';
-- 영해 — 시외 `영해`(NAI3641101) 와 같은 곳
UPDATE bus_terminal SET lat = 36.53853276, lng = 129.40458956 WHERE code = 'NAEK842';
-- 예산 — 시외 `예산`(NAI3242801) 와 같은 곳
UPDATE bus_terminal SET lat = 36.69446343, lng = 126.83234898 WHERE code = 'NAEK398';
-- 예천 — 시외 `예천`(NAI3682601) 와 같은 곳
UPDATE bus_terminal SET lat = 36.64834705, lng = 128.44320494 WHERE code = 'NAEK851';
-- 오산 — 시외 `오산`(NAI1813701) 와 같은 곳
UPDATE bus_terminal SET lat = 37.14635067, lng = 127.06683979 WHERE code = 'NAEK127';
-- 옥과 — 시외 `옥과`(NAI5750401) 와 같은 곳
UPDATE bus_terminal SET lat = 35.27641431, lng = 127.13659134 WHERE code = 'NAEK588';
-- 옥천 — 시외 `옥천`(NAI2903301) 와 같은 곳
UPDATE bus_terminal SET lat = 36.30700525, lng = 127.56429464 WHERE code = 'NAEK410';
-- 옥천 — 시외 `옥천`(NAI2903301) 와 같은 곳
UPDATE bus_terminal SET lat = 36.30700525, lng = 127.56429464 WHERE code = 'NAEK921';
-- 온정 — 시외 `온정`(NAI3635801) 와 같은 곳
UPDATE bus_terminal SET lat = 36.72078254, lng = 129.34448585 WHERE code = 'NAEK856';
-- 온정(EZ) — 시외 `온정`(NAI3635801) 와 같은 곳
UPDATE bus_terminal SET lat = 36.72078254, lng = 129.34448585 WHERE code = 'NAEK860';
-- 완도 — 시외 `완도`(NAI5911401) 와 같은 곳
UPDATE bus_terminal SET lat = 34.31857143, lng = 126.74495481 WHERE code = 'NAEK575';
-- 용인 — 시외 `용인`(NAI1706301) 와 같은 곳
UPDATE bus_terminal SET lat = 37.23287601, lng = 127.20970248 WHERE code = 'NAEK150';
-- 울산 — 시외 `울산(태화)`(NAI4467901) 와 같은 곳
UPDATE bus_terminal SET lat = 35.53656233, lng = 129.33972853 WHERE code = 'NAEK715';
-- 울산시외 — 시외 `울산(태화)`(NAI4467901) 와 같은 곳
UPDATE bus_terminal SET lat = 35.53656233, lng = 129.33972853 WHERE code = 'NAEK714';
-- 울진 — 시외 `울진`(NAI3632601) 와 같은 곳
UPDATE bus_terminal SET lat = 36.98372954, lng = 129.39722254 WHERE code = 'NAEK853';
-- 원주 — 시외 `원주`(NAI2638201) 와 같은 곳
UPDATE bus_terminal SET lat = 37.34491889, lng = 127.93071661 WHERE code = 'NAEK240';
-- 유성복합 — 시외 `유성복합`(NAI3417501) 와 같은 곳
UPDATE bus_terminal SET lat = 36.35553950, lng = 127.33033493 WHERE code = 'NAEK360';
-- 의정부 — 시외 `의정부`(NAI1174901) 와 같은 곳
UPDATE bus_terminal SET lat = 37.74521676, lng = 127.05506977 WHERE code = 'NAEK170';
-- 의정부 — 시외 `의정부`(NAI1174901) 와 같은 곳
UPDATE bus_terminal SET lat = 37.74521676, lng = 127.05506977 WHERE code = 'NAEK173';
-- 이천 — 시외 `이천`(NAI1737301) 와 같은 곳
UPDATE bus_terminal SET lat = 37.27742062, lng = 127.44693565 WHERE code = 'NAEK160';
-- 이천시외 — 시외 `이천`(NAI1737301) 와 같은 곳
UPDATE bus_terminal SET lat = 37.27742062, lng = 127.44693565 WHERE code = 'NAEK143';
-- 익산 — 시외 `익산`(NAI5467401) 와 같은 곳
UPDATE bus_terminal SET lat = 35.93129575, lng = 126.94378771 WHERE code = 'NAEK615';
-- 인천 — 시외 `인천`(NAI2224201) 와 같은 곳
UPDATE bus_terminal SET lat = 37.44177794, lng = 126.70148313 WHERE code = 'NAEK100';
-- 임자(대광) — 시외 `임자(대광)`(NAI5880301) 와 같은 곳
UPDATE bus_terminal SET lat = 35.10182613, lng = 126.07349163 WHERE code = 'NAEK593';
-- 장승포 — 시외 `장승포`(NAI5331601) 와 같은 곳
UPDATE bus_terminal SET lat = 34.87538829, lng = 128.73087520 WHERE code = 'NAEK731';
-- 장흥 — 시외 `장흥`(NAI5932401) 와 같은 곳
UPDATE bus_terminal SET lat = 34.67729611, lng = 126.90957512 WHERE code = 'NAEK580';
-- 전주 — 시외 `전주시외터미널`(NAI5493301) 와 같은 곳
UPDATE bus_terminal SET lat = 35.83437999, lng = 127.13267242 WHERE code = 'NAEK600';
-- 전주 — 시외 `전주시외터미널`(NAI5493301) 와 같은 곳
UPDATE bus_terminal SET lat = 35.83437999, lng = 127.13267242 WHERE code = 'NAEK601';
-- 전주 — 시외 `전주시외터미널`(NAI5493301) 와 같은 곳
UPDATE bus_terminal SET lat = 35.83437999, lng = 127.13267242 WHERE code = 'NAEK602';
-- 전주 — 시외 `전주시외터미널`(NAI5493301) 와 같은 곳
UPDATE bus_terminal SET lat = 35.83437999, lng = 127.13267242 WHERE code = 'NAEK603';
-- 전주 — 시외 `전주시외터미널`(NAI5493301) 와 같은 곳
UPDATE bus_terminal SET lat = 35.83437999, lng = 127.13267242 WHERE code = 'NAEK604';
-- 전주시외 — 시외 `전주시외터미널`(NAI5493301) 와 같은 곳
UPDATE bus_terminal SET lat = 35.83437999, lng = 127.13267242 WHERE code = 'NAEK609';
-- 점촌 — 시외 `점촌`(NAI3695102) 와 같은 곳
UPDATE bus_terminal SET lat = 36.58612218, lng = 128.19233906 WHERE code = 'NAEK850';
-- 정산 — 시외 `정산`(NAI3334601) 와 같은 곳
UPDATE bus_terminal SET lat = 36.41182881, lng = 126.95001593 WHERE code = 'NAEK392';
-- 정선 — 시외 `정선`(NAI2613201) 와 같은 곳
UPDATE bus_terminal SET lat = 37.37888132, lng = 128.65029260 WHERE code = 'NAEK222';
-- 정읍 — 시외 `정읍`(NAI5615801) 와 같은 곳
UPDATE bus_terminal SET lat = 35.57299966, lng = 126.84544708 WHERE code = 'NAEK630';
-- 정읍 — 시외 `정읍`(NAI5615801) 와 같은 곳
UPDATE bus_terminal SET lat = 35.57299966, lng = 126.84544708 WHERE code = 'NAEK631';
-- 제천 — 시외 `제천`(NAI2716501) 와 같은 곳
UPDATE bus_terminal SET lat = 37.14208645, lng = 128.21085121 WHERE code = 'NAEK450';
-- 조치원 — 시외 `조치원`(NAI3002601) 와 같은 곳
UPDATE bus_terminal SET lat = 36.60197325, lng = 127.30260880 WHERE code = 'NAEK350';
-- 주문진 — 시외 `주문진`(NAI2541901) 와 같은 곳
UPDATE bus_terminal SET lat = 37.88437030, lng = 128.82552236 WHERE code = 'NAEK202';
-- 증평 — 시외 `증평`(NAI2793101) 와 같은 곳
UPDATE bus_terminal SET lat = 36.78584808, lng = 127.58252912 WHERE code = 'NAEK455';
-- 진도 — 시외 `진도`(NAI5892201) 와 같은 곳
UPDATE bus_terminal SET lat = 34.47873407, lng = 126.26349992 WHERE code = 'NAEK590';
-- 진주 — 시외 `진주`(NAI5275901) 와 같은 곳
UPDATE bus_terminal SET lat = 35.17869800, lng = 128.09300646 WHERE code = 'NAEK720';
-- 진주 — 시외 `진주`(NAI5275901) 와 같은 곳
UPDATE bus_terminal SET lat = 35.17869800, lng = 128.09300646 WHERE code = 'NAEK722';
-- 진주시외 — 시외 `진주`(NAI5275901) 와 같은 곳
UPDATE bus_terminal SET lat = 35.17869800, lng = 128.09300646 WHERE code = 'NAEK721';
-- 진해 — 시외 `진해`(NAI5170301) 와 같은 곳
UPDATE bus_terminal SET lat = 35.14464765, lng = 128.66153165 WHERE code = 'NAEK704';
-- 창기리 — 시외 `창기리`(NAI3216201) 와 같은 곳
UPDATE bus_terminal SET lat = 36.57489736, lng = 126.33465414 WHERE code = 'NAEK397';
-- 창원 — 시외 `창원`(NAI5139301) 와 같은 곳
UPDATE bus_terminal SET lat = 35.23633077, lng = 128.63936569 WHERE code = 'NAEK710';
-- 천안 — 시외 `천안`(NAI3112001) 와 같은 곳
UPDATE bus_terminal SET lat = 36.81973608, lng = 127.15633919 WHERE code = 'NAEK310';
-- 청양 — 시외 `청양`(NAI3332601) 와 같은 곳
UPDATE bus_terminal SET lat = 36.45224910, lng = 126.80353307 WHERE code = 'NAEK391';
-- 청주(고속) — 시외 `청주`(NAI2839701) 와 같은 곳
UPDATE bus_terminal SET lat = 36.62533754, lng = 127.43170159 WHERE code = 'NAEK400';
-- 청주(센트럴) — 시외 `청주`(NAI2839701) 와 같은 곳
UPDATE bus_terminal SET lat = 36.62533754, lng = 127.43170159 WHERE code = 'NAEK401';
-- 청주공항 — 시외 `청주공항`(NAI2814201) 와 같은 곳
UPDATE bus_terminal SET lat = 36.72205822, lng = 127.49527212 WHERE code = 'NAEK407';
-- 청주대정류소 — 시외 `청주대정류소`(NAI2848501) 와 같은 곳
UPDATE bus_terminal SET lat = 36.65045243, lng = 127.48698146 WHERE code = 'NAEK405';
-- 청주북부 — 시외 `청주북부터미널`(NAI2812001) 와 같은 곳
UPDATE bus_terminal SET lat = 36.71173817, lng = 127.42725314 WHERE code = 'NAEK406';
-- 춘천 — 시외 `춘천`(NAI2443501) 와 같은 곳
UPDATE bus_terminal SET lat = 37.86469440, lng = 127.71752939 WHERE code = 'NAEK250';
-- 충주 — 시외 `충주`(NAI2736001) 와 같은 곳
UPDATE bus_terminal SET lat = 36.98204148, lng = 127.91488549 WHERE code = 'NAEK420';
-- 태백 — 시외 `태백`(NAI2600701) 와 같은 곳
UPDATE bus_terminal SET lat = 37.17676283, lng = 128.98522153 WHERE code = 'NAEK274';
-- 태안 — 시외 `태안`(NAI3214401) 와 같은 곳
UPDATE bus_terminal SET lat = 36.74805446, lng = 126.30323819 WHERE code = 'NAEK394';
-- 태안 — 시외 `태안`(NAI3214401) 와 같은 곳
UPDATE bus_terminal SET lat = 36.74805446, lng = 126.30323819 WHERE code = 'NAEK495';
-- 통영 — 시외 `통영터미널`(NAI5302001) 와 같은 곳
UPDATE bus_terminal SET lat = 34.88510268, lng = 128.41692892 WHERE code = 'NAEK730';
-- 평택 — 시외 `평택`(NAI1791901) 와 같은 곳
UPDATE bus_terminal SET lat = 36.99036504, lng = 127.08786053 WHERE code = 'NAEK180';
-- 평택시외 — 시외 `평택`(NAI1791901) 와 같은 곳
UPDATE bus_terminal SET lat = 36.99036504, lng = 127.08786053 WHERE code = 'NAEK178';
-- 평해 — 시외 `평해`(NAI3636601) 와 같은 곳
UPDATE bus_terminal SET lat = 36.72575704, lng = 129.44115351 WHERE code = 'NAEK844';
-- 포항 — 시외 `포항`(NAI3776001) 와 같은 곳
UPDATE bus_terminal SET lat = 36.01347278, lng = 129.34967716 WHERE code = 'NAEK830';
-- 포항시외 — 시외 `포항`(NAI3776001) 와 같은 곳
UPDATE bus_terminal SET lat = 36.01347278, lng = 129.34967716 WHERE code = 'NAEK831';
-- 함평 — 시외 `함평`(NAI5715301) 와 같은 곳
UPDATE bus_terminal SET lat = 35.06208898, lng = 126.52335531 WHERE code = 'NAEK581';
-- 해남 — 시외 `해남`(NAI5903801) 와 같은 곳
UPDATE bus_terminal SET lat = 34.57066373, lng = 126.60793646 WHERE code = 'NAEK595';
-- 해미 — 시외 `해미`(NAI3196001) 와 같은 곳
UPDATE bus_terminal SET lat = 36.71256278, lng = 126.54434047 WHERE code = 'NAEK383';
-- 홍성 — 시외 `홍성`(NAI3222001) 와 같은 곳
UPDATE bus_terminal SET lat = 36.60094585, lng = 126.67609654 WHERE code = 'NAEK389';
-- 홍천 — 시외 `홍천`(NAI2513501) 와 같은 곳
UPDATE bus_terminal SET lat = 37.68897141, lng = 127.87870250 WHERE code = 'NAEK242';
-- 화순 — 시외 `화순`(NAI5812001) 와 같은 곳
UPDATE bus_terminal SET lat = 35.05634512, lng = 126.98327223 WHERE code = 'NAEK586';
-- 화천 — 시외 `화천`(NAI2413001) 와 같은 곳
UPDATE bus_terminal SET lat = 38.10449313, lng = 127.70434448 WHERE code = 'NAEK260';
-- 후포 — 시외 `후포`(NAI3636901) 와 같은 곳
UPDATE bus_terminal SET lat = 36.68264739, lng = 129.44204017 WHERE code = 'NAEK857';
-- 흥덕 — 시외 `흥덕`(NAI5641501) 와 같은 곳
UPDATE bus_terminal SET lat = 35.51884562, lng = 126.69918088 WHERE code = 'NAEK634';

-- ── 지오코딩 (97곳) ──
-- 가평 — 가평터미널 · 경기 가평군 가평읍 가화로 51
UPDATE bus_terminal SET lat = 37.82459008, lng = 127.51546568 WHERE code = 'NAEK255';
-- 경기광주 — 광주종합터미널 · 경기 광주시 광주대로 30
UPDATE bus_terminal SET lat = 37.40960883, lng = 127.26130663 WHERE code = 'NAEK151';
-- 고양화정 — 화정터미널상가 · 경기 고양시 덕양구 화신로260번길 74
UPDATE bus_terminal SET lat = 37.63425625, lng = 126.83372705 WHERE code = 'NAEK115';
-- 고한사북 — 고한.사북공영버스터미널 · 강원특별자치도 정선군 고한읍 지장천로 856
UPDATE bus_terminal SET lat = 37.21980708, lng = 128.83460024 WHERE code = 'NAEK273';
-- 고현 — 고현버스터미널 · 경남 거제시 고현천로 10
UPDATE bus_terminal SET lat = 34.89100604, lng = 128.62419764 WHERE code = 'NAEK732';
-- 곡성 — 곡성버스터미널 · 전남광주통합특별시 곡성군 곡성읍 군청로 6
UPDATE bus_terminal SET lat = 35.27887432, lng = 127.29397268 WHERE code = 'NAEK589';
-- 공단 — 왜관공단터미널 · 경북 칠곡군 왜관읍 공단로4길 18-8
UPDATE bus_terminal SET lat = 35.96714892, lng = 128.41886909 WHERE code = 'NAEK937';
-- 광명 — 광명종합터미널 · 경기 광명시 광명역로 51
UPDATE bus_terminal SET lat = 37.41859146, lng = 126.88593715 WHERE code = 'NAEK125';
-- 구리 — 구리시외버스정류장 · 경기 구리시 수택동 360
UPDATE bus_terminal SET lat = 37.60183980, lng = 127.14348866 WHERE code = 'NAEK169';
-- 군산대야 — 대야공용버스터미널 · 전북특별자치도 군산시 대야면 번영로 891
UPDATE bus_terminal SET lat = 35.94664218, lng = 126.81018446 WHERE code = 'NAEK611';
-- 금강 — 금강탱크터미널 · 인천 서해구 가정로37번길 17
UPDATE bus_terminal SET lat = 37.48795786, lng = 126.66920991 WHERE code = 'NAEK923';
-- 금산추부 — 금산시외고속버스터미널 · 충남 금산군 금산읍 후곤천길 77
UPDATE bus_terminal SET lat = 36.10529496, lng = 127.49075998 WHERE code = 'NAEK331';
-- 김해장유 — 장유여객터미널 (2026년 9월 예정) · 경남 김해시 무계로 56
UPDATE bus_terminal SET lat = 35.20064852, lng = 128.81934883 WHERE code = 'NAEK736';
-- 낙동강 — 낙동강 구미환승터미널 · 경북 구미시 도개면 용산3길 122-65
UPDATE bus_terminal SET lat = 36.34378300, lng = 128.30085254 WHERE code = 'NAEK823';
-- 낙동강 — 낙동강 구미환승터미널 · 경북 구미시 도개면 용산3길 122-65
UPDATE bus_terminal SET lat = 36.34378300, lng = 128.30085254 WHERE code = 'NAEK824';
-- 낙산 — 낙산버스터미널 · 강원특별자치도 양양군 강현면 동해대로 3087
UPDATE bus_terminal SET lat = 38.11766526, lng = 128.62827945 WHERE code = 'NAEK243';
-- 내서 — 내서고속버스터미널 · 경남 창원시 마산회원구 내서읍 유통단지로 35
UPDATE bus_terminal SET lat = 35.25573980, lng = 128.51317494 WHERE code = 'NAEK706';
-- 노력항 — 장흥노력항여객선터미널 · 전남광주통합특별시 장흥군 회진면 노력도1길 465
UPDATE bus_terminal SET lat = 34.44585263, lng = 126.96547091 WHERE code = 'NAEK579';
-- 대구혁신 — 롯데글로벌로지스 동대구SUB터미널 · 대구 동구 혁신대로 85
UPDATE bus_terminal SET lat = 35.88057926, lng = 128.70792650 WHERE code = 'NAEK809';
-- 대산 — 대산버스터미널 · 충남 서산시 대산읍 정자동4로 8
UPDATE bus_terminal SET lat = 36.93913564, lng = 126.43056351 WHERE code = 'NAEK385';
-- 대신 — 대신터미널 · 경기 여주시 대신면 여양로 1480
UPDATE bus_terminal SET lat = 37.37353939, lng = 127.58398125 WHERE code = 'NAEK932';
-- 대전도룡 — 도룡동고속시외무인정류소 · 대전 유성구 도룡동 401-2
UPDATE bus_terminal SET lat = 36.38872397, lng = 127.37729778 WHERE code = 'NAEK307';
-- 대전청사(샘머리) — 대전청사고속버스둔산정류장 · 대전 서구 둔산동 908
UPDATE bus_terminal SET lat = 36.36141363, lng = 127.39058525 WHERE code = 'NAEK305';
-- 동광양(중마) — GS25 동광양터미널점 · 전남광주통합특별시 광양시 공영로 91
UPDATE bus_terminal SET lat = 34.93600904, lng = 127.69929753 WHERE code = 'NAEK525';
-- 동대구 — 동대구고속터미널 퀵서비스 · 대구 동구 동부로34길 64
UPDATE bus_terminal SET lat = 35.87282169, lng = 128.63308354 WHERE code = 'NAEK800';
-- 동대구 — 동대구고속터미널 퀵서비스 · 대구 동구 동부로34길 64
UPDATE bus_terminal SET lat = 35.87282169, lng = 128.63308354 WHERE code = 'NAEK801';
-- 동대구 — 동대구고속터미널 퀵서비스 · 대구 동구 동부로34길 64
UPDATE bus_terminal SET lat = 35.87282169, lng = 128.63308354 WHERE code = 'NAEK802';
-- 동대구 — 동대구고속터미널 퀵서비스 · 대구 동구 동부로34길 64
UPDATE bus_terminal SET lat = 35.87282169, lng = 128.63308354 WHERE code = 'NAEK803';
-- 동대구 — 동대구고속터미널 퀵서비스 · 대구 동구 동부로34길 64
UPDATE bus_terminal SET lat = 35.87282169, lng = 128.63308354 WHERE code = 'NAEK804';
-- 동대구 — 동대구고속터미널 퀵서비스 · 대구 동구 동부로34길 64
UPDATE bus_terminal SET lat = 35.87282169, lng = 128.63308354 WHERE code = 'NAEK806';
-- 동대구 — 동대구고속터미널 퀵서비스 · 대구 동구 동부로34길 64
UPDATE bus_terminal SET lat = 35.87282169, lng = 128.63308354 WHERE code = 'NAEK808';
-- 동래 — 동래시외버스정류소 · 부산 동래구 중앙대로1325번길 24
UPDATE bus_terminal SET lat = 35.20572087, lng = 129.07656883 WHERE code = 'NAEK887';
-- 동해 — 동해시종합버스터미널 · 강원특별자치도 동해시 동해대로 5443
UPDATE bus_terminal SET lat = 37.52770657, lng = 129.10404236 WHERE code = 'NAEK210';
-- 모바일 — 북부모바일 중고나라 북부터미널점 · 대구 서구 서대구로 303-10
UPDATE bus_terminal SET lat = 35.88481755, lng = 128.55613314 WHERE code = 'NAEK968';
-- 밀양 — 밀양버스터미널 · 경남 밀양시 북성로 7
UPDATE bus_terminal SET lat = 35.49880937, lng = 128.74368681 WHERE code = 'NAEK750';
-- 범계 — 범계공항리무진버스정류소 · 경기 안양시 동안구 시민대로159번길 25
UPDATE bus_terminal SET lat = 37.39086285, lng = 126.94790409 WHERE code = 'NAEK481';
-- 봉동 — 더리터 봉동터미널점 · 전북특별자치도 완주군 봉동읍 봉동동서로 131
UPDATE bus_terminal SET lat = 35.93883343, lng = 127.16639422 WHERE code = 'NAEK649';
-- 봉화 — 봉화공용정류장 · 경북 봉화군 봉화읍 봉화로 1153
UPDATE bus_terminal SET lat = 36.89124433, lng = 128.73600071 WHERE code = 'NAEK858';
-- 부산 — 부산종합버스터미널 · 부산 금정구 중앙대로 2238
UPDATE bus_terminal SET lat = 35.28477323, lng = 129.09547241 WHERE code = 'NAEK700';
-- 부산사상 — 부산서부버스터미널 · 부산 사상구 사상로 201
UPDATE bus_terminal SET lat = 35.16323947, lng = 128.98252543 WHERE code = 'NAEK703';
-- 부산시외 — 부산종합버스터미널 · 부산 금정구 중앙대로 2238
UPDATE bus_terminal SET lat = 35.28477323, lng = 129.09547241 WHERE code = 'NAEK701';
-- 삼호 — 삼호터미널 · 전남광주통합특별시 영암군 삼호읍 용앙리 1657-6
UPDATE bus_terminal SET lat = 34.75442731, lng = 126.45149197 WHERE code = 'NAEK591';
-- 서대구 — 서대구고속버스터미널 · 대구 북구 팔달로 103
UPDATE bus_terminal SET lat = 35.89026310, lng = 128.56141474 WHERE code = 'NAEK805';
-- 서천 — 서천버스터미널 · 충남 서천군 서천읍 서천로 31
UPDATE bus_terminal SET lat = 36.07548625, lng = 126.69585963 WHERE code = 'NAEK384';
-- 서충주 — 서충주고속버스정류소 · 충북 충주시 중앙탑면 용전리 228-11
UPDATE bus_terminal SET lat = 37.02228076, lng = 127.84940881 WHERE code = 'NAEK419';
-- 서충주 — 서충주고속버스정류소 · 충북 충주시 중앙탑면 용전리 228-11
UPDATE bus_terminal SET lat = 37.02228076, lng = 127.84940881 WHERE code = 'NAEK421';
-- 선산 — 선산시외버스터미널 · 경북 구미시 선산읍 선산대로 1408
UPDATE bus_terminal SET lat = 36.24021575, lng = 128.30439018 WHERE code = 'NAEK812';
-- 선산 — 선산시외버스터미널 · 경북 구미시 선산읍 선산대로 1408
UPDATE bus_terminal SET lat = 36.24021575, lng = 128.30439018 WHERE code = 'NAEK813';
-- 세종시외 — 세종고속시외버스터미널 · 세종특별자치시 갈매로 37-12
UPDATE bus_terminal SET lat = 36.46922276, lng = 127.27365746 WHERE code = 'NAEK348';
-- 순천종합 — 순천종합버스터미널 · 전남광주통합특별시 순천시 장천3길 13
UPDATE bus_terminal SET lat = 34.94759306, lng = 127.49136191 WHERE code = 'NAEK516';
-- 시흥시화 — 현대내자터미널 시화사업소 · 경기 시흥시 옥구천동로 120
UPDATE bus_terminal SET lat = 37.33660519, lng = 126.71152881 WHERE code = 'NAEK195';
-- 신성 — 신성탱크터미널 · 전남광주통합특별시 여수시 여수산단4로 166-36
UPDATE bus_terminal SET lat = 34.83894193, lng = 127.67335453 WHERE code = 'NAEK920';
-- 신철원 — 신철원터미널 · 강원특별자치도 철원군 갈말읍 명성로 154
UPDATE bus_terminal SET lat = 38.14539682, lng = 127.30814681 WHERE code = 'NAEK147';
-- 아산(둔포) — 아산고속버스터미널 · 충남 아산시 번영로 223
UPDATE bus_terminal SET lat = 36.78446199, lng = 127.01535027 WHERE code = 'NAEK339';
-- 아산온양 — 온양농협 터미널지점 · 충남 아산시 번영로 226
UPDATE bus_terminal SET lat = 36.78387893, lng = 127.01561678 WHERE code = 'NAEK340';
-- 안성공도 — 공도시외버스정류장 · 경기 안성시 공도읍 공도로 51-7
UPDATE bus_terminal SET lat = 36.99880318, lng = 127.16653728 WHERE code = 'NAEK133';
-- 안양(경) — 안양역시외버스정류장 · 경기 안양시 만안구 만안로223번길 25
UPDATE bus_terminal SET lat = 37.40113627, lng = 126.92170148 WHERE code = 'NAEK482';
-- 안중오거리 — 안중버스터미널 · 경기 평택시 안중읍 안현로서9길 14-4
UPDATE bus_terminal SET lat = 36.97767743, lng = 126.92334932 WHERE code = 'NAEK176';
-- 안천 — 안천버스정류소 · 전북특별자치도 진안군 안천면 진무로 2980
UPDATE bus_terminal SET lat = 35.89655207, lng = 127.55096194 WHERE code = 'NAEK651';
-- 언양 — 언양임시시외버스터미널 · 울산 울주군 언양읍 언양로 24
UPDATE bus_terminal SET lat = 35.56260990, lng = 129.12978671 WHERE code = 'NAEK891';
-- 영산포 — 영산포공용터미널 · 전남광주통합특별시 나주시 예향로 3803
UPDATE bus_terminal SET lat = 34.99463597, lng = 126.71272892 WHERE code = 'NAEK565';
-- 영암 — 영암여객자동차터미널 · 전남광주통합특별시 영암군 영암읍 동문로 8
UPDATE bus_terminal SET lat = 34.79677641, lng = 126.70270050 WHERE code = 'NAEK570';
-- 영춘 — 세븐일레븐 단양영춘터미널점 · 충북 단양군 영춘면 온달평강로 36
UPDATE bus_terminal SET lat = 37.07674789, lng = 128.48478199 WHERE code = 'NAEK462';
-- 오천 — 오천항여객터미널 · 충남 보령시 오천면 오천해안로 782-13
UPDATE bus_terminal SET lat = 36.43960038, lng = 126.52144492 WHERE code = 'NAEK142';
-- 완도항 — 완도공용버스터미널 · 전남광주통합특별시 완도군 완도읍 개포로130번길 20
UPDATE bus_terminal SET lat = 34.31857143, lng = 126.74495481 WHERE code = 'NAEK574';
-- 왜관 — 왜관북부버스정류장 · 경북 칠곡군 왜관읍 중앙로 250
UPDATE bus_terminal SET lat = 35.99716006, lng = 128.39906619 WHERE code = 'NAEK935';
-- 용인신갈 — 신갈시외버스정류장 · 경기 용인시 기흥구 신갈동 469-2
UPDATE bus_terminal SET lat = 37.27145424, lng = 127.10432581 WHERE code = 'NAEK111';
-- 원동 — 원동버스터미널 · 전남광주통합특별시 완도군 군외면 청해진서로 2184
UPDATE bus_terminal SET lat = 34.39232018, lng = 126.64884882 WHERE code = 'NAEK578';
-- 원주문막 — 문막고속시외버스정류소 · 강원특별자치도 원주시 문막읍 여원로 2175
UPDATE bus_terminal SET lat = 37.31976029, lng = 127.83047097 WHERE code = 'NAEK245';
-- 유구 — 유구터미널 · 충남 공주시 유구읍 유구마곡사로 9
UPDATE bus_terminal SET lat = 36.55039037, lng = 126.95205121 WHERE code = 'NAEK321';
-- 익산팔봉 — 익산팔봉정류소 · 전북특별자치도 익산시 팔봉동 453-8
UPDATE bus_terminal SET lat = 35.96537078, lng = 127.01156058 WHERE code = 'NAEK616';
-- 인천공항T1 — 인천공항1버스터미널 · 인천 영종구 운서동 2851
UPDATE bus_terminal SET lat = 37.44863374, lng = 126.45154018 WHERE code = 'NAEK105';
-- 인천공항T2 — 인천공항2버스터미널 · 인천 영종구 제2터미널대로 444
UPDATE bus_terminal SET lat = 37.46858748, lng = 126.43380753 WHERE code = 'NAEK117';
-- 임자(진리) — 임자진리여객선터미널 · 전남광주통합특별시 신안군 임자면 임자로 3
UPDATE bus_terminal SET lat = 35.08138819, lng = 126.12115837 WHERE code = 'NAEK594';
-- 장성 — 장성공용버스터미널 · 전남광주통합특별시 장성군 장성읍 영천로 125-7
UPDATE bus_terminal SET lat = 35.29680802, lng = 126.78069949 WHERE code = 'NAEK583';
-- 전북강진 — 강진공용버스터미널 · 전북특별자치도 임실군 강진면 호국로 10
UPDATE bus_terminal SET lat = 35.52942616, lng = 127.16087200 WHERE code = 'NAEK644';
-- 지도 — 지도여객자동차터미널 · 전남광주통합특별시 신안군 지도읍 해제지도로 1240
UPDATE bus_terminal SET lat = 35.05950587, lng = 126.21115308 WHERE code = 'NAEK585';
-- 진안 — 진안시외버스공용정류장 · 전북특별자치도 진안군 진안읍 진무로 1120
UPDATE bus_terminal SET lat = 35.79214179, lng = 127.43095147 WHERE code = 'NAEK650';
-- 진주개양 — 개양고속버스정류장 · 경남 진주시 가좌길74번길 10-1
UPDATE bus_terminal SET lat = 35.15943584, lng = 128.10921554 WHERE code = 'NAEK723';
-- 창원역 — 창원역시외고속버스정류소 · 경남 창원시 의창구 의창대로 67
UPDATE bus_terminal SET lat = 35.25671980, lng = 128.60687157 WHERE code = 'NAEK711';
-- 천안아산역 — 천안아산고속버스정류장 · 충남 아산시 배방읍 장재리 1765
UPDATE bus_terminal SET lat = 36.79375353, lng = 127.10751637 WHERE code = 'NAEK343';
-- 청평 — 청평터미널 · 경기 가평군 청평면 청평중앙로 54
UPDATE bus_terminal SET lat = 37.73811929, lng = 127.42085638 WHERE code = 'NAEK252';
-- 태인 — 태인터미널 · 전북특별자치도 정읍시 태인면 태인로 26
UPDATE bus_terminal SET lat = 35.65136893, lng = 126.94146128 WHERE code = 'NAEK629';
-- 통도사 — 통도사신평버스터미널 · 경남 양산시 하북면 통도사로 30
UPDATE bus_terminal SET lat = 35.49321051, lng = 129.08680570 WHERE code = 'NAEK890';
-- 통도사신평 — 통도사신평버스터미널 · 경남 양산시 하북면 통도사로 30
UPDATE bus_terminal SET lat = 35.49321051, lng = 129.08680570 WHERE code = 'NAEK746';
-- 파주문산 — 투루카 북파주농협 본점 주차장(문산시외버스터미널) · 경기 파주시 문산읍 문향로 75
UPDATE bus_terminal SET lat = 37.85858123, lng = 126.78544200 WHERE code = 'NAEK045';
-- 평택대 — 평택대터미널 · 경기 평택시 서동대로 3826
UPDATE bus_terminal SET lat = 36.99288465, lng = 127.13235567 WHERE code = 'NAEK175';
-- 포천 — 포천공영버스터미널 · 경기 포천시 신읍동 42-11
UPDATE bus_terminal SET lat = 37.89746655, lng = 127.20364357 WHERE code = 'NAEK146';
-- 풍기 — 풍기택배터미널 · 경북 영주시 봉현면 신재로 896
UPDATE bus_terminal SET lat = 36.86457891, lng = 128.52515933 WHERE code = 'NAEK834';
-- 해제 — 해제여객터미널 · 전남광주통합특별시 무안군 해제면 양간로 17
UPDATE bus_terminal SET lat = 35.11196188, lng = 126.29152422 WHERE code = 'NAEK552';
-- 황간 — 황간임시정류장 · 충북 영동군 황간면 마산리 2-4
UPDATE bus_terminal SET lat = 36.22544788, lng = 127.91499351 WHERE code = 'NAEK440';
-- 황간 — 황간임시정류장 · 충북 영동군 황간면 마산리 2-4
UPDATE bus_terminal SET lat = 36.22544788, lng = 127.91499351 WHERE code = 'NAEK928';
-- 회진 — 회진시외버스터미널 · 전남광주통합특별시 장흥군 회진면 회진로 425
UPDATE bus_terminal SET lat = 34.48521826, lng = 126.93919300 WHERE code = 'NAEK577';
-- 횡계 — 횡계시외버스터미널 · 강원특별자치도 평창군 대관령면 대관령로 78
UPDATE bus_terminal SET lat = 37.67314613, lng = 128.70496667 WHERE code = 'NAEK235';
-- 횡성 — 횡성시외버스터미널 · 강원특별자치도 횡성군 횡성읍 횡성로 377
UPDATE bus_terminal SET lat = 37.48747764, lng = 127.98407931 WHERE code = 'NAEK238';
-- 횡성 — 횡성시외버스터미널 · 강원특별자치도 횡성군 횡성읍 횡성로 377
UPDATE bus_terminal SET lat = 37.48747764, lng = 127.98407931 WHERE code = 'NAEK239';
-- 횡성시외 — 횡성시외버스터미널 · 강원특별자치도 횡성군 횡성읍 횡성로 377
UPDATE bus_terminal SET lat = 37.48747764, lng = 127.98407931 WHERE code = 'NAEK241';

-- ── 손으로 바로잡음 (3곳) ──
-- 세종터미널(연구) — 세종연구단지고속버스정류장 · 세종 반곡동 — 세종고속시외버스터미널과 다른 곳이다
UPDATE bus_terminal SET lat = 36.49597000, lng = 127.30538000 WHERE code = 'NAEK358';
-- 철원 — 동송시외버스공용터미널 · 강원 철원군 동송읍 금학로 215 — 신철원(갈말)과 12km 떨어진 다른 터미널이다
UPDATE bus_terminal SET lat = 38.20776000, lng = 127.21803000 WHERE code = 'NAEK148';
-- 홍대조치원 — 홍대세종정류장 · 세종 조치원읍 신안리 — 조치원터미널과 다른 곳이다
UPDATE bus_terminal SET lat = 36.62166000, lng = 127.29087000 WHERE code = 'NAEK354';


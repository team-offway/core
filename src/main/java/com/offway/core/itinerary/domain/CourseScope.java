package com.offway.core.itinerary.domain;

import com.offway.core.itinerary.repository.CourseRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * "내 코스" 목록을 무엇으로 볼지 — 다가오는 여행 · 지난 여행 · 전부.
 *
 * <p>상수별로 <b>조회 방법을 스스로</b> 갖는다. 서비스에 {@code if (scope == UPCOMING) ...} 분기를 쌓지 않기 위해서다.
 *
 * <p>당일치기는 시작일과 종료일이 같아 예전과 결과가 다르지 않다.
 *
 * <p><b>여행 날짜가 없는 코스</b>는 {@link #ALL} 에만 나온다. #91 이전에 저장된 코스가 그렇고, 날짜가 없으면 다가오는
 * 여행인지 지난 여행인지 판단할 근거 자체가 없다 — 아무 쪽에나 끼워 넣으면 화면이 조용히 거짓말을 한다.
 */
public enum CourseScope {

    /**
     * <b>종료일</b>이 오늘 포함 이후(#325). 가까운 여행이 위로 온다 — 화면이 D-day 순으로 보여준다.
     *
     * <p><b>여행 중인 코스는 여기 남는다.</b> 시작일로 가르면 2박3일 여행 둘째 날에 그 코스가 이미
     * "지난 여행" 으로 넘어가는데, 앱의 칩은 종료일 기준이라 아직 D-DAY 다 — 다녀온 여행 탭에 D-DAY
     * 코스가 앉아 있게 된다.
     */
    UPCOMING {
        @Override
        public List<Course> find(CourseRepository repository, UUID userId, LocalDate today) {
            return repository.findUpcoming(userId, today);
        }

        @Override
        public Page<Course> find(
                CourseRepository repository, UUID userId, LocalDate today, Pageable pageable) {
            return repository.findUpcoming(userId, today, pageable);
        }
    },

    /**
     * <b>종료일</b>이 오늘보다 앞(#325). 최근 여행이 위로 온다.
     *
     * <p>탭·칩·"다녀오셨나요?" 모달이 <b>종료일 다음 날 함께</b> 넘어간다. 모달은 이미 종료일로
     * 걸렀으므로 이 변경으로 달라지지 않는다 — 종료일이 지난 코스는 여전히 여기 있다.
     */
    PAST {
        @Override
        public List<Course> find(CourseRepository repository, UUID userId, LocalDate today) {
            return repository.findPast(userId, today);
        }

        @Override
        public Page<Course> find(
                CourseRepository repository, UUID userId, LocalDate today, Pageable pageable) {
            return repository.findPast(userId, today, pageable);
        }
    },

    /** 전부 — 저장한 순서(최근 저장이 위). 날짜 없는 코스도 여기 나온다. */
    ALL {
        @Override
        public List<Course> find(CourseRepository repository, UUID userId, LocalDate today) {
            return repository.findByUserId(userId);
        }

        @Override
        public Page<Course> find(
                CourseRepository repository, UUID userId, LocalDate today, Pageable pageable) {
            return repository.findByUserId(userId, pageable);
        }
    };

    public abstract List<Course> find(CourseRepository repository, UUID userId, LocalDate today);

    /** 같은 범위를 한 페이지만 — 목록 화면이 쓴다(#105). 정렬은 범위가 이미 소유한다. */
    public abstract Page<Course> find(
            CourseRepository repository, UUID userId, LocalDate today, Pageable pageable);
}

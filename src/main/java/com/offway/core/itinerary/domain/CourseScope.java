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
 * <p><b>여행 날짜가 없는 코스</b>는 {@link #ALL} 에만 나온다. #91 이전에 저장된 코스가 그렇고, 날짜가 없으면 다가오는
 * 여행인지 지난 여행인지 판단할 근거 자체가 없다 — 아무 쪽에나 끼워 넣으면 화면이 조용히 거짓말을 한다.
 */
public enum CourseScope {

    /** 오늘 포함 이후. 가까운 여행이 위로 온다 — 화면이 D-day 순으로 보여준다. */
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

    /** 오늘 이전. 최근 여행이 위로 온다. */
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

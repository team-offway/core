package com.offway.core.device.domain;

/**
 * 푸시 토큰을 발급한 기기 종류(#264).
 *
 * <p>문자열 코드 대신 enum 으로 받는다 — 오타(`ios`·`Iphone`)가 값으로 저장되면 나중에 플랫폼별로 발송을
 * 나눌 때 그 행들을 아무 데도 못 넣는다. 모르는 값은 요청 경계에서 400 으로 걸러진다.
 *
 * <p>지금은 이 둘뿐이다. 웹 푸시가 생기면 그때 더한다.
 */
public enum DevicePlatform {

    IOS,

    ANDROID
}

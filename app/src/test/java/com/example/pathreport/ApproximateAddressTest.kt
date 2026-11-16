package com.example.pathreport

import org.junit.Assert.assertEquals
import org.junit.Test

class ApproximateAddressTest {
    @Test fun hidesBuildingNumbersButKeepsRoadNameDigits() {
        assertEquals("가상특별시 가상구 가상샘플8길 부근", approximateAddress("대한민국 가상특별시 가상구 가상샘플8길 3"))
        assertEquals("가상특별시 가상구 가상샘플로15마길 부근", approximateAddress("대한민국 가상특별시 가상구 가상샘플로15마길 28 101동 203호"))
        assertEquals("가상특별시 가상구 가상대로14길 부근", approximateAddress("대한민국 가상특별시 가상구 가상대로14길 74 (가상동)"))
    }
    @Test fun hidesLotNumbersAndUnrecognizedDetails() {
        assertEquals("가상특별시 가상구 가상동 부근", approximateAddress("가상특별시 가상구 가상동 123-45 101동 203호"))
        assertEquals("등록 장소 부근", approximateAddress("123 Main Street Apt 4"))
        assertEquals("등록 장소 부근", approximateAddress(""))
    }
    @Test fun usesLandmarksOnlyWhenRoadAndDistrictAreMissing() {
        assertEquals("가상샘플역 부근", approximateAddress("가상특별시 가상샘플역"))
        assertEquals("가상쉼터공원 부근", approximateAddress("대한민국 가상특별시 가상쉼터공원"))
        assertEquals("가상하우스 부근", approximateAddress("가상특별시 가상하우스 101동 203호"))
        assertEquals("가상특별시 가상구 가상샘플로 부근", approximateAddress("가상특별시 가상구 가상샘플로 123 가상샘플역"))
        assertEquals("가상특별시 가상구 가상동 부근", approximateAddress("가상특별시 가상구 가상동 123 가상샘플역"))
        assertEquals("가상특별시 부근", approximateAddress("가상특별시"))
    }
    @Test fun usesPlaceNamesWithoutKnownSuffixesAndOmitsDetails() {
        assertEquals("가상쉼터 부근", approximateAddress("가상특별시 가상쉼터 (가상기관)"))
        assertEquals("가상쉼터 부근", approximateAddress("대한민국 가상특별시 가상구 가상쉼터 (가상기관)"))
        assertEquals("가상쉼터 부근", approximateAddress("가상특별시 가상쉼터 101동 203호"))
        assertEquals("행복한 쉼터 부근", approximateAddress("가상특별시 행복한 쉼터 (본관)"))
        assertEquals("가상특별시 부근", approximateAddress("가상특별시 101동 203호"))
    }
}
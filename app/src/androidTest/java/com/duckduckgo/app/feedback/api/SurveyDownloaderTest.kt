/*
 * Copyright (c) 2018 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.feedback.api

import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.feedback.db.SurveyDao
import com.duckduckgo.app.feedback.model.Survey
import com.duckduckgo.app.feedback.model.Survey.Status.NOT_ALLOCATED
import com.duckduckgo.app.feedback.model.Survey.Status.SCHEDULED
import com.nhaarman.mockito_kotlin.*
import okhttp3.ResponseBody
import org.junit.Rule
import org.junit.Test
import retrofit2.Call
import retrofit2.Response

class SurveyDownloaderTest {
    private var mockDao: SurveyDao = mock()
    private var mockService: SurveyService = mock()
    private var mockCall: Call<SurveyGroup?> = mock()
    private var testee = SurveyDownloader(mockService, mockDao)

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @Test
    fun whenNewSurveyAllocatedThenSavedAsScheduledAndOldSurveysCancelled() {
        whenever(mockCall.execute()).thenReturn(Response.success(surveyWithAllocation("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockDao).insert(Survey("abc", SURVEY_URL, 7, SCHEDULED))
        verify(mockDao).cancelScheduledSurveys()
    }

    @Test
    fun whenNewSurveyNotAllocatedThenSavedAsUnallocatedAndOldSurveysCancelled() {
        whenever(mockCall.execute()).thenReturn(Response.success(surveyNoAllocation("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockDao).insert(Survey("abc", null, null, NOT_ALLOCATED))
        verify(mockDao).cancelScheduledSurveys()
    }

    @Test
    fun whenSurveyAlreadyExistsThenNotSavedAndOldSurveysNotCancelled() {
        whenever(mockDao.exists(any())).thenReturn(true)
        whenever(mockCall.execute()).thenReturn(Response.success(surveyWithAllocation("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockDao, never()).insert(any())
        verify(mockDao, never()).cancelScheduledSurveys()
    }

    @Test
    fun whenSuccessfulRequestReturnsNoSurveysThenOldSurveysCancelled() {
        whenever(mockCall.execute()).thenReturn(Response.success(null))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockDao).cancelScheduledSurveys()
    }

    @Test(expected = RuntimeException::class)
    fun whenRequestUnsuccessfulThenExceptionThrown() {
        whenever(mockCall.execute()).thenReturn(Response.error(500, ResponseBody.create(null, "")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
    }

    private fun surveyWithAllocation(id: String): SurveyGroup {
        val surveyOptions = listOf(
            SurveyGroup.SurveyOption(SURVEY_URL, 1, 0.0),
            SurveyGroup.SurveyOption(SURVEY_URL, 7, 1.0)
        )
        return SurveyGroup(id, surveyOptions)
    }

    private fun surveyNoAllocation(id: String): SurveyGroup {
        val surveyOptions = listOf(
            SurveyGroup.SurveyOption(SURVEY_URL, 1, 0.0),
            SurveyGroup.SurveyOption(SURVEY_URL, 7, 0.0)
        )
        return SurveyGroup(id, surveyOptions)
    }

    companion object {
        const val SURVEY_URL = "https://survey.com"
    }
}
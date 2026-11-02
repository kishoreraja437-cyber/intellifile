package com.simats.intellifile

import com.simats.intellifile.data.model.UserDto
import org.junit.Assert.*
import org.junit.Test

class ProfileCachingTest {

    @Test
    fun testUserDtoEqualityAndCopy() {
        val original = UserDto(
            id = 5,
            full_name = "Kishore Raja",
            email = "kishore@gmail.com",
            profile_image = "https://example.com/avatar.jpg"
        )

        val updated = original.copy(
            full_name = "Kishore Updated",
            email = "kishore_new@gmail.com"
        )

        assertEquals(5, updated.id)
        assertEquals("Kishore Updated", updated.full_name)
        assertEquals("kishore_new@gmail.com", updated.email)
        assertEquals("https://example.com/avatar.jpg", updated.profile_image)
    }

    @Test
    fun testCacheStrategyLogic() {
        var apiCallCount = 0
        var cachedUser: UserDto? = null
        var isProfileCached = false

        fun getProfile(forceRefresh: Boolean = false): UserDto {
            if (!forceRefresh && isProfileCached && cachedUser != null) {
                return cachedUser!!
            }
            // Simulate network call
            apiCallCount++
            val fetchedUser = UserDto(
                id = 18,
                full_name = "Kishore Raja",
                email = "kishoreraja437@gmail.com"
            )
            cachedUser = fetchedUser
            isProfileCached = true
            return fetchedUser
        }

        // First opening: no cached data -> API call occurs
        assertEquals(0, apiCallCount)
        val firstResult = getProfile(forceRefresh = false)
        assertEquals(1, apiCallCount)
        assertEquals("Kishore Raja", firstResult.full_name)

        // Subsequent openings (2nd, 3rd, 4th): cached data exists -> NO API call!
        val secondResult = getProfile(forceRefresh = false)
        assertEquals(1, apiCallCount)
        assertEquals("Kishore Raja", secondResult.full_name)

        val thirdResult = getProfile(forceRefresh = false)
        assertEquals(1, apiCallCount)
        assertEquals("Kishore Raja", thirdResult.full_name)

        // Explicit refresh: forceRefresh == true -> API call occurs
        val refreshedResult = getProfile(forceRefresh = true)
        assertEquals(2, apiCallCount)
        assertEquals("Kishore Raja", refreshedResult.full_name)

        // After refresh, subsequent calls still use cache -> NO API call!
        val fifthResult = getProfile(forceRefresh = false)
        assertEquals(2, apiCallCount)

        // Logout: clear cache
        cachedUser = null
        isProfileCached = false

        // First opening after new login -> API call occurs once again
        val afterLogoutResult = getProfile(forceRefresh = false)
        assertEquals(3, apiCallCount)
    }
}


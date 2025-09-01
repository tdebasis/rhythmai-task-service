package com.rhythmai.task.security

import com.rhythmai.task.model.UserContext
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class AuthUtils {
    
    companion object {
        private const val USER_ID_HEADER = "X-User-Id"
        private const val USER_EMAIL_HEADER = "X-User-Email"
        private const val USER_NAME_HEADER = "X-User-Name"
        private const val USER_PROFILE_PICTURE_HEADER = "X-User-Profile-Picture"
    }
    
    /**
     * Extract user context from BFF headers
     * The Express.js BFF will inject these headers after authentication
     */
    fun extractUserContext(request: HttpServletRequest): UserContext? {
        val userId = request.getHeader(USER_ID_HEADER)
        val email = request.getHeader(USER_EMAIL_HEADER)
        val name = request.getHeader(USER_NAME_HEADER)
        
        return if (userId != null && email != null && name != null) {
            UserContext(
                userId = userId,
                email = email,
                name = name,
                profilePicture = request.getHeader(USER_PROFILE_PICTURE_HEADER)
            )
        } else {
            null
        }
    }
    
    /**
     * Validate that user context is present and valid
     */
    fun validateUserContext(userContext: UserContext?): Boolean {
        return userContext != null && 
               userContext.userId.isNotBlank() && 
               userContext.email.isNotBlank() &&
               userContext.name.isNotBlank()
    }
}
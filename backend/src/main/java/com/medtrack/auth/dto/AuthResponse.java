package com.medtrack.auth.dto; public record AuthResponse(String accessToken,String tokenType,long expiresIn,String email,String role){}

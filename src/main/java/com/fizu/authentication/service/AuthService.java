package com.fizu.authentication.service;

import java.util.Map;

public interface AuthService {
    Map<String,String> loginWithGoogle(String idToken);
}

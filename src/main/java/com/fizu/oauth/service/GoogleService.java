package com.fizu.oauth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

public interface GoogleService {
    GoogleIdToken.Payload verify(String idClient);
}

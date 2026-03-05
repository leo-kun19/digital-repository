package com.example.thesisrepo.service;

import com.example.thesisrepo.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OidcProvisioningUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

  private final AuthProvisioningService authProvisioningService;
  private final OidcUserService delegate = new OidcUserService();

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) {
    OidcUser oidcUser = delegate.loadUser(userRequest);
    AuthProvisioningService.ProvisioningResult provisioning = authProvisioningService.provision(oidcUser);
    User user = provisioning.user();

    List<GrantedAuthority> authorities =
      List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

    return new DefaultOidcUser(
      authorities,
      oidcUser.getIdToken(),
      oidcUser.getUserInfo(),
      provisioning.nameAttributeKey()
    );
  }
}

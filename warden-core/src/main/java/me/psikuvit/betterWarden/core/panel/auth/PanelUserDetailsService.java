package me.psikuvit.betterWarden.core.panel.auth;

import me.psikuvit.betterWarden.core.model.PanelUser;
import me.psikuvit.betterWarden.core.repo.PanelUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PanelUserDetailsService implements UserDetailsService {

    private final PanelUserRepository users;

    public PanelUserDetailsService(PanelUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        PanelUser user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build();
    }
}

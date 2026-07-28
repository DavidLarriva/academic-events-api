package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.AuthResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.LoginRequestDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.RefreshTokenRequestDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.RegisterRequestDto;

public interface AuthService {

    AuthResponseDto register(RegisterRequestDto request, String clientIp);

    AuthResponseDto login(LoginRequestDto request, String clientIp);

    AuthResponseDto refresh(RefreshTokenRequestDto request, String clientIp);

    void logout(RefreshTokenRequestDto request);
}

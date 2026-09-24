import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  function setup(isAuthenticated: boolean) {
    const authService = { isAuthenticated: () => isAuthenticated } as Partial<AuthService>;
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authService }, provideRouter([])],
    });
  }

  it('allows navigation when authenticated', () => {
    setup(true);
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(result).toBe(true);
  });

  it('redirects to /login when not authenticated', () => {
    setup(false);
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(result).not.toBe(true);
    expect(router.serializeUrl(result as never)).toBe('/login');
  });
});

import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { guestGuard } from './guest.guard';

describe('guestGuard', () => {
  function setup(isAuthenticated: boolean, isAdmin: boolean) {
    const authService = {
      isAuthenticated: () => isAuthenticated,
      isAdmin: () => isAdmin,
    } as Partial<AuthService>;
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authService }, provideRouter([])],
    });
  }

  it('allows an anonymous visitor to reach /login', () => {
    setup(false, false);
    const result = TestBed.runInInjectionContext(() => guestGuard({} as never, {} as never));
    expect(result).toBe(true);
  });

  it('redirects an already-authenticated USER away from /login', () => {
    setup(true, false);
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() => guestGuard({} as never, {} as never));
    expect(router.serializeUrl(result as never)).toBe('/dashboard');
  });

  it('redirects an already-authenticated ADMIN to /admin', () => {
    setup(true, true);
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() => guestGuard({} as never, {} as never));
    expect(router.serializeUrl(result as never)).toBe('/admin');
  });
});

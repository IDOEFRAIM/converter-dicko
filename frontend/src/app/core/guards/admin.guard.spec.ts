import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { adminGuard } from './admin.guard';

describe('adminGuard', () => {
  function setup(isAuthenticated: boolean, isAdmin: boolean) {
    const authService = {
      isAuthenticated: () => isAuthenticated,
      isAdmin: () => isAdmin,
    } as Partial<AuthService>;
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authService }, provideRouter([])],
    });
  }

  it('allows an authenticated ADMIN', () => {
    setup(true, true);
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    expect(result).toBe(true);
  });

  it('redirects an authenticated USER to /dashboard, never showing the admin screen', () => {
    setup(true, false);
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    expect(router.serializeUrl(result as never)).toBe('/dashboard');
  });

  it('redirects an anonymous visitor to /login', () => {
    setup(false, false);
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    expect(router.serializeUrl(result as never)).toBe('/login');
  });
});

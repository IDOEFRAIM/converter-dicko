import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let httpMock: HttpTestingController;
  let http: HttpClient;
  let router: Router;
  let logoutSpy: ReturnType<typeof vi.fn>;

  function setup(wasAuthenticated: boolean) {
    logoutSpy = vi.fn();
    const authService = {
      isAuthenticated: () => wasAuthenticated,
      logout: logoutSpy,
    } as unknown as AuthService;

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authService },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    http = TestBed.inject(HttpClient);
    router = TestBed.inject(Router);
  }

  afterEach(() => httpMock.verify());

  it('logs out and redirects to /login on a 401 while a session was active', () => {
    setup(true);
    const navigateSpy = vi.spyOn(router, 'navigate');

    http.get('/api/v1/orders').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/v1/orders').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(logoutSpy).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/login'], { queryParams: { sessionExpired: true } });
  });

  it('does not redirect on a 401 received while already logged out (e.g. login failure)', () => {
    setup(false);
    const navigateSpy = vi.spyOn(router, 'navigate');

    http.post('/api/auth/login', {}).subscribe({ error: () => undefined });
    httpMock.expectOne('/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(navigateSpy).not.toHaveBeenCalled();
  });

  it('leaves non-401 errors untouched (no logout, no redirect)', () => {
    setup(true);
    const navigateSpy = vi.spyOn(router, 'navigate');

    http.get('/api/v1/orders/123').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/v1/orders/123').flush({}, { status: 404, statusText: 'Not Found' });

    expect(logoutSpy).not.toHaveBeenCalled();
    expect(navigateSpy).not.toHaveBeenCalled();
  });
});

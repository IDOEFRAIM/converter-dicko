import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { LoginPage } from './login.page';

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginPage);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('does not call the API when the form is invalid', () => {
    fixture.componentInstance.submit();
    httpMock.expectNone('/api/auth/login');
  });

  it('logs in and routes a USER to /home', () => {
    const navigateSpy = vi.spyOn(router, 'navigate');
    fixture.componentInstance.form.setValue({ phone: '+2250700000000', password: 'secret123' });

    fixture.componentInstance.submit();

    httpMock.expectOne('/api/auth/login').flush({
      data: {
        accessToken: 'jwt',
        tokenType: 'Bearer',
        expiresAt: '2026-01-01T00:00:00Z',
        user: { id: 'u1', phone: '+2250700000000', firstName: 'A', lastName: 'B', email: null, status: 'ACTIVE', roles: ['USER'], createdAt: '', lastLoginAt: null },
      },
      message: 'ok',
    });

    expect(navigateSpy).toHaveBeenCalledWith(['/home']);
  });

  it('routes an ADMIN to /admin and surfaces a backend error otherwise', () => {
    const navigateSpy = vi.spyOn(router, 'navigate');
    fixture.componentInstance.form.setValue({ phone: '+2250700000000', password: 'wrong' });

    fixture.componentInstance.submit();
    httpMock.expectOne('/api/auth/login').flush(
      { code: 'INVALID_CREDENTIALS', message: 'Identifiants invalides.' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(navigateSpy).not.toHaveBeenCalled();
    expect(fixture.componentInstance.errorMessage()).toBe('Identifiants invalides.');
  });
});

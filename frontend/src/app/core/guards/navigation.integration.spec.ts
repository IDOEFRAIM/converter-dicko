import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AuthService } from '../services/auth.service';

/**
 * Verifie le parcours de navigation principal au niveau du routeur
 * (guards + redirections reelles), plutot que chaque garde isolement :
 * un visiteur anonyme ne peut jamais atteindre une page protegee, et un
 * simple USER ne peut jamais atteindre l'espace ADMIN — jamais l'ecran
 * ne s'affiche, meme brievement.
 */
describe('Navigation principale', () => {
  async function navigate(url: string) {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return TestBed.inject(Router).url;
  }

  it('redirects an anonymous visitor from /dashboard to /login', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(routes),
        { provide: AuthService, useValue: { isAuthenticated: () => false, isAdmin: () => false, currentUser: () => null } },
      ],
    });

    expect(await navigate('/dashboard')).toBe('/login');
  });

  it('redirects an authenticated USER away from /admin to /dashboard', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(routes),
        { provide: AuthService, useValue: { isAuthenticated: () => true, isAdmin: () => false, currentUser: () => ({ firstName: 'Test', lastName: 'User' }) } },
      ],
    });

    expect(await navigate('/admin/orders')).toBe('/dashboard');
  });

  it('lets an authenticated ADMIN reach the admin area', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(routes),
        { provide: AuthService, useValue: { isAuthenticated: () => true, isAdmin: () => true, currentUser: () => ({ firstName: 'Admin', lastName: 'User' }) } },
      ],
    });

    expect(await navigate('/admin')).toBe('/admin/dashboard');
  });

  it('redirects an already-authenticated visitor away from /login', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(routes),
        { provide: AuthService, useValue: { isAuthenticated: () => true, isAdmin: () => false, currentUser: () => ({ firstName: 'Test', lastName: 'User' }) } },
      ],
    });

    expect(await navigate('/login')).toBe('/dashboard');
  });
});

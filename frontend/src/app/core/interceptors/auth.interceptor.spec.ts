import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { TokenStorageService } from '../services/token-storage.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let httpMock: HttpTestingController;
  let http: HttpClient;
  let tokenStorage: TokenStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    http = TestBed.inject(HttpClient);
    tokenStorage = TestBed.inject(TokenStorageService);
    // localStorage survit d'un test a l'autre au sein du meme environnement
    // jsdom : repartir d'un etat propre avant chaque cas.
    tokenStorage.clear();
  });

  afterEach(() => httpMock.verify());

  it('attaches the Bearer token to /api requests when one is stored', () => {
    tokenStorage.setToken('a-jwt-token');

    http.get('/api/v1/orders').subscribe();

    const req = httpMock.expectOne('/api/v1/orders');
    expect(req.request.headers.get('Authorization')).toBe('Bearer a-jwt-token');
    req.flush({});
  });

  it('does not attach a header when no token is stored', () => {
    http.get('/api/v1/orders').subscribe();

    const req = httpMock.expectOne('/api/v1/orders');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('never attaches the token to a non-API request', () => {
    tokenStorage.setToken('a-jwt-token');

    http.get('/assets/logo.png').subscribe();

    const req = httpMock.expectOne('/assets/logo.png');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });
});

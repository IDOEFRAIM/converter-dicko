import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PreferredRatePage } from './preferred-rate.page';

function pageOf(content: unknown[]) {
  return { data: { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true }, message: 'ok' };
}

const WAITING_REQUEST = {
  id: 'pr1',
  direction: 'XOF_TO_CNY',
  amountXof: '100000.00',
  targetRate: '90.000000',
  currentRate: '86.275000',
  gap: -3.725,
  phase: 'WAITING',
  status: 'ACTIVE',
  achievedRate: null,
  createdAt: '2026-01-01T00:00:00Z',
  expiresAt: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString(),
  executedAt: null,
  expiredAt: null,
  cancelledAt: null,
  exchange: null,
};

const EXCHANGE_REQUEST = {
  ...WAITING_REQUEST,
  id: 'pr2',
  phase: 'EXCHANGE_IN_PROGRESS',
  status: 'EXECUTED',
  currentRate: null,
  gap: null,
  achievedRate: '90.000000',
  exchange: {
    id: 'ex1',
    amountXof: '100000.00',
    achievedRate: '90.000000',
    amountCny: '1111.11',
    status: 'STARTED',
    stage: 'STARTED',
    startedAt: new Date().toISOString(),
    deadlineAt: new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString(),
    nextUpdateAt: new Date(Date.now() + 45 * 60 * 1000).toISOString(),
    completedAt: null,
  },
};

describe('PreferredRatePage', () => {
  let fixture: ComponentFixture<PreferredRatePage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PreferredRatePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(PreferredRatePage);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('shows the WAITING view (target/current/gap/J+3 countdown) and never the exchange view', () => {
    fixture.detectChanges();
    httpMock.expectOne((req) => req.url === '/api/v1/preferred-rates').flush(pageOf([WAITING_REQUEST]));
    fixture.detectChanges();

    const host: HTMLElement = fixture.nativeElement;
    expect(host.querySelector('[data-testid="waiting-view"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="exchange-view"]')).toBeNull();
    const text = host.textContent ?? '';
    expect(text).toContain('90.000000');
    expect(text).toContain('86.275000');
    expect(text).toContain('-3.725');
    expect(text).toContain('En attente du taux');
  });

  it('shows the EXCHANGE_IN_PROGRESS view (achieved rate/progress/2h countdown) and never the waiting view', () => {
    fixture.detectChanges();
    httpMock.expectOne((req) => req.url === '/api/v1/preferred-rates').flush(pageOf([EXCHANGE_REQUEST]));
    fixture.detectChanges();
    httpMock.expectOne((req) => req.url === '/api/v1/notifications').flush(pageOf([]));
    fixture.detectChanges();

    const host: HTMLElement = fixture.nativeElement;
    expect(host.querySelector('[data-testid="exchange-view"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="waiting-view"]')).toBeNull();
    const text = host.textContent ?? '';
    expect(text).toContain('Echange en cours');
    expect(text).toContain('90.000000');
    // Le compte a rebours J+3 (temps restant du WAITING) ne doit jamais apparaitre pendant un echange.
    expect(text).not.toContain('delai de 3 jours');
  });
});

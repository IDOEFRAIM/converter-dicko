import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RateHistoryPage } from './rate-history.page';
import { PublicRateHistoryEntry } from '../../../core/models/rate-history.model';

function page(content: PublicRateHistoryEntry[]) {
  return { data: { content, page: 0, size: 60, totalElements: content.length, totalPages: 1, first: true, last: true }, message: 'ok' };
}

describe('RateHistoryPage', () => {
  let fixture: ComponentFixture<RateHistoryPage>;
  let httpMock: HttpTestingController;

  async function createWith(content: PublicRateHistoryEntry[]) {
    await TestBed.configureTestingModule({
      imports: [RateHistoryPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(RateHistoryPage);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/v1/rates/history').flush(page(content));
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('renders only the customer rate, never a break-even/margin/internal figure', async () => {
    await createWith([{ currencyPair: 'XOF/CNY', customerRate: '87.007700', recordedAt: '2026-02-01T10:00:00Z' }]);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('87.007700');
    expect(text.toLowerCase()).not.toContain('breakeven');
    expect(text.toLowerCase()).not.toContain('marge');
  });

  it('shows the empty state with a refresh action when there is no history', async () => {
    await createWith([]);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Aucun historique disponible');
    const button = fixture.nativeElement.querySelector('button');
    expect(button.textContent).toContain('Actualiser');
  });

  it('shows an error state with a retry action on API failure, never a stale chart', async () => {
    await TestBed.configureTestingModule({
      imports: [RateHistoryPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(RateHistoryPage);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/v1/rates/history').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("Impossible de charger l'historique du taux.");
    expect(fixture.nativeElement.querySelector('svg')).toBeNull();
  });

  it('computes the variation strictly from two real published points, never a fabricated "today" figure', async () => {
    await createWith([
      { currencyPair: 'XOF/CNY', customerRate: '87.40', recordedAt: '2026-02-02T10:00:00Z' },
      { currencyPair: 'XOF/CNY', customerRate: '87.00', recordedAt: '2026-02-01T10:00:00Z' },
    ]);
    const variation = fixture.componentInstance.variation();
    expect(variation).not.toBeNull();
    expect(variation!.percent).toBeCloseTo(((87.4 - 87.0) / 87.0) * 100, 5);
  });

  it('never computes a variation with fewer than two points', async () => {
    await createWith([{ currencyPair: 'XOF/CNY', customerRate: '87.00', recordedAt: '2026-02-01T10:00:00Z' }]);
    expect(fixture.componentInstance.variation()).toBeNull();
  });

  it('derives min/max strictly from the loaded series', async () => {
    await createWith([
      { currencyPair: 'XOF/CNY', customerRate: '88.00', recordedAt: '2026-02-03T10:00:00Z' },
      { currencyPair: 'XOF/CNY', customerRate: '85.00', recordedAt: '2026-02-02T10:00:00Z' },
      { currencyPair: 'XOF/CNY', customerRate: '86.50', recordedAt: '2026-02-01T10:00:00Z' },
    ]);
    expect(fixture.componentInstance.rangeLabel()).toEqual({ min: 85, max: 88 });
  });
});

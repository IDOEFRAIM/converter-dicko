import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CountdownTimerComponent } from './countdown-timer.component';

describe('CountdownTimerComponent', () => {
  let fixture: ComponentFixture<CountdownTimerComponent>;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({ imports: [CountdownTimerComponent] }).compileComponents();
    fixture = TestBed.createComponent(CountdownTimerComponent);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('displays a countdown while the quote is still active', () => {
    fixture.componentRef.setInput('expiresAt', new Date(Date.now() + 5 * 60 * 1000).toISOString());
    fixture.detectChanges();

    expect(fixture.componentInstance.expired()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Expire dans');
  });

  it('flags urgency under the 5 minute mark', () => {
    fixture.componentRef.setInput('expiresAt', new Date(Date.now() + 60 * 1000).toISOString());
    fixture.detectChanges();

    expect(fixture.componentInstance.urgent()).toBe(true);
  });

  it('emits expiredChange exactly once when the deadline passes', () => {
    fixture.componentRef.setInput('expiresAt', new Date(Date.now() + 2000).toISOString());
    fixture.detectChanges();

    const emitted: void[] = [];
    fixture.componentInstance.expiredChange.subscribe(() => emitted.push(undefined));

    vi.advanceTimersByTime(1000);
    fixture.detectChanges();
    expect(fixture.componentInstance.expired()).toBe(false);
    expect(emitted.length).toBe(0);

    vi.advanceTimersByTime(2000);
    fixture.detectChanges();
    expect(fixture.componentInstance.expired()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Devis expire');
    expect(emitted.length).toBe(1);

    // Le tick suivant ne doit pas re-emettre l'evenement.
    vi.advanceTimersByTime(1000);
    fixture.detectChanges();
    expect(emitted.length).toBe(1);
  });
});

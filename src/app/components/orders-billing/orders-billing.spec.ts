import { ComponentFixture, TestBed } from '@angular/core/testing';

import { OrdersBilling } from './orders-billing';

describe('OrdersBilling', () => {
  let component: OrdersBilling;
  let fixture: ComponentFixture<OrdersBilling>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrdersBilling],
    }).compileComponents();

    fixture = TestBed.createComponent(OrdersBilling);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

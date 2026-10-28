import { FormControl, FormGroup } from '@angular/forms';

import { PASSWORD_PATTERN, passwordsMatch } from './passwords';

describe('passwords', () => {
  it('needs a letter and a digit, in any language', () => {
    expect(PASSWORD_PATTERN.test('Candidate1')).toBe(true);
    expect(PASSWORD_PATTERN.test('كلمةسر1')).toBe(true);
    expect(PASSWORD_PATTERN.test('onlyletters')).toBe(false);
    expect(PASSWORD_PATTERN.test('12345678')).toBe(false);
  });

  it('marks the confirm field only while the passwords differ', () => {
    const group = new FormGroup(
      { password: new FormControl('abc12345'), confirm: new FormControl('') },
      { validators: passwordsMatch },
    );
    expect(group.get('confirm')?.errors).toEqual({ mismatch: true });
    group.get('confirm')?.setValue('abc12345');
    expect(group.get('confirm')?.errors).toBeNull();
  });
});

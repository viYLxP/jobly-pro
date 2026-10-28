import { AbstractControl, ValidationErrors } from '@angular/forms';

// Same rule as the backend (AuthDtos.PASSWORD_PATTERN): a letter and a digit. The length is a separate validator.
// نفس قاعدة الخادم (AuthDtos.PASSWORD_PATTERN): حرف ورقم. والطول له validator منفصل.
export const PASSWORD_PATTERN = /^(?=.*\p{L})(?=.*\d).+$/u;

/** Group validator: marks the confirm field when the two passwords differ. / يعلّم حقل التأكيد لو كلمتا المرور مختلفتين. */
export function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password');
  const confirm = group.get('confirm');
  if (!password || !confirm) return null;
  const mismatch = password.value !== confirm.value;
  if (mismatch) {
    confirm.setErrors({ ...confirm.errors, mismatch: true });
  } else if (confirm.errors?.['mismatch']) {
    const rest = { ...confirm.errors };
    delete rest['mismatch'];
    confirm.setErrors(Object.keys(rest).length ? rest : null);
  }
  return null;
}

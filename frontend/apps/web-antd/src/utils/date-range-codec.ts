/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import type { Dayjs } from 'dayjs';

import dayjs from 'dayjs';

type DateRange = [Dayjs, Dayjs];

type OptionalDateRangeField<
  TFormValues extends Record<string, unknown>,
  TRangeField extends keyof TFormValues,
> =
  Record<never, never> extends Pick<TFormValues, TRangeField>
    ? [Exclude<TFormValues[TRangeField], undefined>] extends [never]
      ? never
      : Exclude<TFormValues[TRangeField], undefined> extends DateRange
        ? TRangeField
        : never
    : never;

type DateRangeSubmitValues<
  TFormValues extends Record<string, unknown>,
  TRangeField extends keyof TFormValues,
  TStartField extends string,
  TEndField extends string,
> = Omit<TFormValues, TRangeField> &
  Record<TEndField | TStartField, string | undefined>;

interface DateRangeCodecOptions<
  TFormValues extends Record<string, unknown>,
  TRangeField extends keyof TFormValues & string,
  TStartField extends string,
  TEndField extends string,
> {
  endField: TEndField;
  rangeField: OptionalDateRangeField<TFormValues, TRangeField> & TRangeField;
  startField: TStartField;
}

export function createDateRangeCodec<
  TFormValues extends Record<string, unknown>,
>() {
  return function createCodec<
    TRangeField extends keyof TFormValues & string,
    TStartField extends string,
    TEndField extends string,
  >(
    options: DateRangeCodecOptions<
      TFormValues,
      TRangeField,
      TStartField,
      TEndField
    >,
  ) {
    const { endField, startField } = options;
    const rangeField = options.rangeField as TRangeField;
    type SubmitValues = DateRangeSubmitValues<
      TFormValues,
      TRangeField,
      TStartField,
      TEndField
    >;

    return {
      decode(values: Readonly<SubmitValues>): TFormValues {
        const { [endField]: end, [startField]: start, ...formValues } = values;
        return {
          ...formValues,
          ...(start && end ? { [rangeField]: [dayjs(start), dayjs(end)] } : {}),
        } as TFormValues;
      },
      encode(values: Readonly<TFormValues>): SubmitValues {
        const { [rangeField]: value, ...formValues } = values;
        const range = value as DateRange | undefined;
        return {
          ...formValues,
          [endField]: range?.[1]?.format('YYYY-MM-DD'),
          [startField]: range?.[0]?.format('YYYY-MM-DD'),
        } as SubmitValues;
      },
    };
  };
}

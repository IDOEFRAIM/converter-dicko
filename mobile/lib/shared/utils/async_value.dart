/// Etat d'une valeur chargee depuis le reseau — loading/data/error, sans
/// jamais melanger les trois dans des booleans separes desynchronisables
/// (mission section 38 : chaque ecran reseau doit gerer loading/success/
/// empty/error ; "empty" est une simple propriete de [data], pas un 4e etat).
sealed class AsyncValue<T> {
  const AsyncValue();

  const factory AsyncValue.loading() = AsyncLoading<T>;

  const factory AsyncValue.data(T value) = AsyncData<T>;

  const factory AsyncValue.error(String message) = AsyncError<T>;

  R when<R>({
    required R Function() loading,
    required R Function(T value) data,
    required R Function(String message) error,
  }) {
    final self = this;
    if (self is AsyncLoading<T>) return loading();
    if (self is AsyncData<T>) return data(self.value);
    if (self is AsyncError<T>) return error(self.message);
    throw StateError('Unreachable AsyncValue variant');
  }
}

final class AsyncLoading<T> extends AsyncValue<T> {
  const AsyncLoading();
}

final class AsyncData<T> extends AsyncValue<T> {
  final T value;

  const AsyncData(this.value);
}

final class AsyncError<T> extends AsyncValue<T> {
  final String message;

  const AsyncError(this.message);
}

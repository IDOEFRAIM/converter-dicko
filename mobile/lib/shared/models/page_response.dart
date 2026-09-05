/// Page generique renvoyee par tout endpoint de liste du backend — miroir de
/// `com.converter.common.api.PageResponse` / `PageResponse<T>` (Angular).
class PageResponse<T> {
  final List<T> content;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final bool first;
  final bool last;

  const PageResponse({
    required this.content,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.first,
    required this.last,
  });

  factory PageResponse.fromJson(Map<String, dynamic> json, T Function(Map<String, dynamic>) fromJsonT) {
    return PageResponse<T>(
      content: (json['content'] as List<dynamic>? ?? const [])
          .map((e) => fromJsonT(e as Map<String, dynamic>))
          .toList(growable: false),
      page: json['page'] as int? ?? 0,
      size: json['size'] as int? ?? 0,
      totalElements: json['totalElements'] as int? ?? 0,
      totalPages: json['totalPages'] as int? ?? 0,
      first: json['first'] as bool? ?? true,
      last: json['last'] as bool? ?? true,
    );
  }

  bool get isEmpty => content.isEmpty;
}

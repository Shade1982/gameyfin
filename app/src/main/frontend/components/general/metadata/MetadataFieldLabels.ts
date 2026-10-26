export const METADATA_FIELD_LABELS: Record<string, string> = {
    title: "Title",
    platforms: "Platforms",
    release: "Release",
    summary: "Summary",
    developers: "Developers",
    publishers: "Publishers",
    userRating: "User rating",
    criticRating: "Critic rating",
    genres: "Genres",
    themes: "Themes",
    keywords: "Keywords",
    features: "Features",
    perspectives: "Perspectives",
    coverImage: "Cover",
    headerImage: "Header",
    images: "Screenshots",
    videoUrls: "Videos"
};

export function metadataFieldLabel(field: string): string {
    return METADATA_FIELD_LABELS[field] ?? field;
}

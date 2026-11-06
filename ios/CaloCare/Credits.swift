import SwiftUI

/// Photo and data credits, as the licences require: Creative Commons photos from Wikimedia Commons, and packaged
/// food data from Open Food Facts under the Open Database Licence (ODbL).
struct CreditsView: View {
    private let photos = PhotoCredit.load()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Card(L("Dữ liệu thực phẩm", "Packaged food data")) {
                    Text(L("Thông tin sản phẩm đóng gói lấy từ Open Food Facts, dữ liệu mở theo giấy phép ODbL.",
                           "Packaged food details come from Open Food Facts, open data under the ODbL licence."))
                        .font(.subheadline)
                }
                Card(L("Ảnh món ăn", "Dish photos")) {
                    ForEach(photos) { photo in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(photo.name).font(.subheadline.weight(.medium))
                            Text("\(photo.author) · \(photo.license)").font(.caption).foregroundColor(.secondary)
                            if let link = URL(string: photo.url) {
                                Link(L("Xem trên Wikimedia Commons", "View on Wikimedia Commons"), destination: link).font(.caption)
                            }
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
            .padding(16)
        }
        .background(Palette.paper)
        .navigationTitle(L("Nguồn ảnh và dữ liệu", "Photo and data credits"))
    }
}

struct PhotoCredit: Identifiable {
    var id: String
    var name: String
    var license: String
    var author: String
    var url: String

    /// Reads FoodPhotos/CREDITS.tsv from the app bundle: tab-separated id, name, file, licence, author, link.
    static func load() -> [PhotoCredit] {
        let file = Bundle.main.url(forResource: "CREDITS", withExtension: "tsv")
            ?? Bundle.main.url(forResource: "CREDITS", withExtension: "tsv", subdirectory: "FoodPhotos")
        guard let file, let text = try? String(contentsOf: file, encoding: .utf8) else { return [] }
        return text.split(whereSeparator: \.isNewline).compactMap { line in
            let columns = line.split(separator: "\t", omittingEmptySubsequences: false).map(String.init)
            guard columns.count >= 6 else { return nil }
            return PhotoCredit(id: columns[0], name: columns[1], license: columns[3], author: columns[4], url: columns[5])
        }
    }
}

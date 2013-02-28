/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/** 
 * An XML handler for the Amazon return 
 * 
 * An example response looks like;
 * <ItemSearchResponse xmlns="http://webservices.amazon.com/AWSECommerceService/2005-10-05">
 * 		<OperationRequest>
 * 			<HTTPHeaders>
 * 				<Header Name="UserAgent" Value="Mozilla/5.0 (X11; U; Linux i686; en-US) AppleWebKit/533.4 (KHTML, like Gecko) Chrome/5.0.375.29 Safari/533.4">
 * 				</Header>
 * 			</HTTPHeaders>
 * 			<RequestId>df6342c0-c939-4b28-877a-5096da83a959</RequestId>
 * 			<Arguments>
 * 				<Argument Name="Operation" Value="ItemSearch"></Argument>
 * 				<Argument Name="Service" Value="AWSECommerceService"></Argument>
 * 				<Argument Name="Signature" Value="xHegBeDoF4U5vlC66493SULuAgQulFQSJyqHz3s2obE="></Argument>
 * 				<Argument Name="Keywords" Value="9780006483830"></Argument>
 * 				<Argument Name="Timestamp" Value="2010-05-16T01:30:12.000Z"></Argument>
 * 				<Argument Name="ResponseGroup" Value="Medium,Images"></Argument>
 * 				<Argument Name="SubscriptionId" Value="AKIAIHF2BM6OTOA23JEQ"></Argument>
 * 				<Argument Name="SearchIndex" Value="Books"></Argument>
 * 			</Arguments>
 * 			<RequestProcessingTime>0.0740140000000000</RequestProcessingTime>
 * 		</OperationRequest>
 * 		<Items>
 * 			<Request>
 * 				<IsValid>True</IsValid>
 * 				<ItemSearchRequest>
 * 					<Condition>New</Condition>
 * 					<DeliveryMethod>Ship</DeliveryMethod>
 * 					<Keywords>9780006483830</Keywords>
 * 					<MerchantId>Amazon</MerchantId>
 * 					<ResponseGroup>Medium</ResponseGroup>
 * 					<ResponseGroup>Images</ResponseGroup>
 * 					<SearchIndex>Books</SearchIndex>
 * 				</ItemSearchRequest>
 * 			</Request>
 * 			<TotalResults>1</TotalResults>
 * 			<TotalPages>1</TotalPages>
 * 			<Item>
 * 				<ASIN>0006483836</ASIN>
 * 				<DetailPageURL>http://www.amazon.com/TRIGGER-CLARKE-KUBE-MCDOWELL/dp/0006483836%3FSubscriptionId%3DAKIAIHF2BM6OTOA23JEQ%26tag%3Dws%26linkCode%3Dxm2%26camp%3D2025%26creative%3D165953%26creativeASIN%3D0006483836</DetailPageURL>
 * 				<SalesRank>4575972</SalesRank>
 * 				<SmallImage>
 * 					<URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL._SL75_.jpg</URL>
 * 					<Height Units="pixels">75</Height>
 * 					<Width Units="pixels">48</Width>
 * 				</SmallImage>
 * 				<MediumImage>
 * 					<URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL._SL160_.jpg</URL>
 * 					<Height Units="pixels">160</Height>
 * 					<Width Units="pixels">102</Width>
 * 				</MediumImage>
 * 				<LargeImage>
 * 					<URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL.jpg</URL>
 * 					<Height Units="pixels">475</Height>
 * 					<Width Units="pixels">304</Width>
 * 				</LargeImage>
 * 				<ImageSets>
 * 					<ImageSet Category="primary">
 * 						<SwatchImage>
 * 							<URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL._SL30_.jpg</URL>
 * 							<Height Units="pixels">30</Height>
 * 							<Width Units="pixels">19</Width>
 * 						</SwatchImage>
 * 						<SmallImage>
 * 							<URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL._SL75_.jpg</URL>
 * 							<Height Units="pixels">75</Height>
 * 							<Width Units="pixels">48</Width>
 * 						</SmallImage>
 * 						<MediumImage>
 * 							<URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL._SL160_.jpg</URL>
 * 							<Height Units="pixels">160</Height>
 * 							<Width Units="pixels">102</Width>
 * 						</MediumImage>
 * 						<LargeImage>
 * 							<URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL.jpg</URL>
 * 							<Height Units="pixels">475</Height>
 * 							<Width Units="pixels">304</Width>
 * 						</LargeImage>
 * 					</ImageSet>
 * 				</ImageSets>
 * 				<ItemAttributes>
 * 					<Author>CLARKE / KUBE-MCDOWELL</Author>
 * 					<Binding>Paperback</Binding>
 * 					<DeweyDecimalNumber>823.914</DeweyDecimalNumber>
 * 					<EAN>9780006483830</EAN>
 * 					<Edition>New Ed</Edition>
 * 					<Format>Import</Format>
 * 					<ISBN>0006483836</ISBN>
 * 					<Label>Voyager</Label>
 * 					<Languages>
 * 						<Language>
 * 							<Name>English</Name>
 * 							<Type>Original Language</Type>
 * 						</Language>
 * 						<Language>
 * 							<Name>English</Name>
 * 							<Type>Unknown</Type>
 * 						</Language>
 * 						<Language>
 * 							<Name>English</Name>
 * 							<Type>Published</Type>
 * 						</Language>
 * 					</Languages>
 * 					<Manufacturer>Voyager</Manufacturer>
 * 					<NumberOfPages>560</NumberOfPages>
 * 					<PackageDimensions>
 * 						<Height Units="hundredths-inches">140</Height>
 * 						<Length Units="hundredths-inches">680</Length>
 * 						<Weight Units="hundredths-pounds">60</Weight>
 * 						<Width Units="hundredths-inches">430</Width>
 * 					</PackageDimensions>
 * 					<ProductGroup>Book</ProductGroup>
 * 					<PublicationDate>2000-01-01</PublicationDate>
 * 					<Publisher>Voyager</Publisher>
 * 					<Studio>Voyager</Studio>
 * 					<Title>TRIGGER</Title>
 * 				</ItemAttributes>
 * 				<OfferSummary>
 * 					<LowestUsedPrice>
 * 						<Amount>1</Amount>
 * 						<CurrencyCode>USD</CurrencyCode>
 * 						<FormattedPrice>$0.01</FormattedPrice>
 * 					</LowestUsedPrice>
 * 					<TotalNew>0</TotalNew>
 * 					<TotalUsed>43</TotalUsed>
 * 					<TotalCollectible>0</TotalCollectible>
 * 					<TotalRefurbished>0</TotalRefurbished>
 * 				</OfferSummary>
 * 			</Item>
 * 		</Items>
 * </ItemSearchResponse>
 * 
 * @author evan
 * 
 */
public class SearchAmazonHandler extends DefaultHandler {
	private Bundle mBookData;
	private StringBuilder mBuilder;
	private String mThumbnailUrl = "";
	private int mThumbnailSize = -1;
	private static boolean mFetchThumbnail;
	
	/* How many results found */
	public int count = 0;
	/* A flag to identify if we are in the correct node */
	private boolean mInLanguage = false;
	private boolean mInListPrice = false;
	private String mCurrencyCode = "";
	private String mCurrencyAmount = "";
	private boolean entry = false;
	private boolean image = false;
	private boolean done = false;
	
	/* The XML element names */
	public static String ID = "id";
	public static String TOTALRESULTS = "TotalResults";
	public static String ENTRY = "Item";
	public static String AUTHOR = "Author";
	public static String TITLE = "Title";
	public static String ISBN = "EAN";
	public static String EISBN = "EISBN";
	public static String ISBNOLD = "ISBN";
	public static String DATE_PUBLISHED = "PublicationDate";
	public static String PUBLISHER = "Publisher";
	public static String PAGES = "NumberOfPages";
	public static String THUMBNAIL = "URL";
	public static String SMALLIMAGE = "SmallImage";
	public static String MEDIUMIMAGE = "MediumImage";
	public static String LARGEIMAGE = "LargeImage";
	public static String DESCRIPTION = "Content";
	public static String BINDING = "Binding";
	public static String LANGUAGE = "Language";
	public static String NAME = "Name";
	public static String LIST_PRICE = "ListPrice";
	public static String CURRENCY_CODE = "CurrencyCode";
	public static String AMOUNT = "Amount";

	SearchAmazonHandler(Bundle bookData, boolean fetchThumbnail) {
		mBookData = bookData;
		mFetchThumbnail = fetchThumbnail;
	}
//	/*
//	 * A public function the return a book structure
//	 */
//	public String[] getBook(){
//		String[] book = {author, title, isbn, publisher, date_published, rating,  bookshelf, read, series, pages, series_num, list_price, anthology, location, read_start, read_end, audiobook, signed, description, genre};
//		return book;
//	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		super.characters(ch, start, length);
		mBuilder.append(ch, start, length);
	}

	/**
	 * Add the current characters to the book collection if not already present.
	 * 
	 * @param key	Key for data to add
	 */
	private void addIfNotPresent(String key) {
		if (!mBookData.containsKey(key) || mBookData.getString(key).length() == 0) {
			mBookData.putString(key, mBuilder.toString());
		}		
	}

	/**
	 * Add the passed characters to the book collection if not already present.
	 * 
	 * @param key	Key for data to add
	 */
	private void addIfNotPresent(String key, String value) {
		if (!mBookData.containsKey(key) || mBookData.getString(key).length() == 0) {
			mBookData.putString(key, value);
		}		
	}

	/**
	 * Add the current characters to the book collection if not already present.
	 * 
	 * @param key	Key for data to add
	 * @param value	Value to compare to; if present but equal to this, it will be overwritten
	 */
	private void addIfNotPresentOrEqual(String key, String value) {
		if (!mBookData.containsKey(key) || mBookData.getString(key).length() == 0 || mBookData.getString(key).equals(value)) {
			mBookData.putString(key, mBuilder.toString());
		}		
	}

	/**
	 * 	Overridden method to get the best thumbnail, if present.
	 */
	@Override
	public void endDocument() {
		if (mFetchThumbnail && mThumbnailUrl.length() > 0) {
			String filename = Utils.saveThumbnailFromUrl(mThumbnailUrl, "_AM");
			if (filename.length() > 0)
				Utils.appendOrAdd(mBookData, "__thumbnail", filename);			
		}		
	}

	/*
	 * (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 * 
	 * Populate the class variables for each appropriate element. 
	 * Also download the thumbnail and store in a tmp location
	 */
	@Override
	public void endElement(String uri, String localName, String name) throws SAXException {
		super.endElement(uri, localName, name);
		try {
			if (localName.equalsIgnoreCase(TOTALRESULTS)){
				count = Integer.parseInt(mBuilder.toString());
			} else if (localName.equalsIgnoreCase(THUMBNAIL)){
				if (image == true) {
					mThumbnailUrl = mBuilder.toString();
					image = false;
				}
			} else if (localName.equalsIgnoreCase(ENTRY)){
				done = true;
				entry = false;
			} else if (localName.equalsIgnoreCase(LANGUAGE)){
				mInLanguage = false;
			} else if (localName.equalsIgnoreCase(LIST_PRICE)){
				if (mCurrencyCode.equalsIgnoreCase("usd") && !mCurrencyAmount.equals("")) {
					try {
						Float price = Float.parseFloat(mCurrencyAmount) / 100;
						addIfNotPresent(CatalogueDBAdapter.KEY_LIST_PRICE, String.format("%.2f", price));
					} catch(Exception e) {
						// Ignore
					}
				}
				mCurrencyCode = "";
				mCurrencyAmount = "";
				mInListPrice = false;
			} else if (entry == true) {
				if (localName.equalsIgnoreCase(AUTHOR)){
					Utils.appendOrAdd(mBookData, CatalogueDBAdapter.KEY_AUTHOR_DETAILS, mBuilder.toString());
				} else if (localName.equalsIgnoreCase(TITLE)){
					addIfNotPresent(CatalogueDBAdapter.KEY_TITLE);
				} else if (localName.equalsIgnoreCase(ISBN) || localName.equalsIgnoreCase(EISBN)){
					String tmp = mBuilder.toString();
					if (!mBookData.containsKey(CatalogueDBAdapter.KEY_ISBN) 
							|| mBookData.getString(CatalogueDBAdapter.KEY_ISBN).length() < tmp.length()) {
						mBookData.putString(CatalogueDBAdapter.KEY_ISBN, tmp);
					}					
				} else if (localName.equalsIgnoreCase(ISBNOLD)){
					String tmp = mBuilder.toString();
					if (!mBookData.containsKey(CatalogueDBAdapter.KEY_ISBN) 
							|| mBookData.getString(CatalogueDBAdapter.KEY_ISBN).length() < tmp.length()) {
						mBookData.putString(CatalogueDBAdapter.KEY_ISBN, tmp);
					}					
				} else if (localName.equalsIgnoreCase(PUBLISHER)){
					addIfNotPresent(CatalogueDBAdapter.KEY_PUBLISHER);
				} else if (localName.equalsIgnoreCase(DATE_PUBLISHED)){
					addIfNotPresent(CatalogueDBAdapter.KEY_DATE_PUBLISHED);
				} else if (localName.equalsIgnoreCase(PAGES)){
					addIfNotPresentOrEqual(CatalogueDBAdapter.KEY_PAGES, "0");
				} else if (localName.equalsIgnoreCase(DESCRIPTION)){
					addIfNotPresent(CatalogueDBAdapter.KEY_DESCRIPTION);
				} else if (localName.equalsIgnoreCase(BINDING)){
					addIfNotPresent(CatalogueDBAdapter.KEY_FORMAT);
				} else if (mInLanguage && localName.equalsIgnoreCase(NAME)){
					addIfNotPresent(CatalogueDBAdapter.KEY_LANGUAGE);
				} else if (mInListPrice && localName.equalsIgnoreCase(AMOUNT)){
					mCurrencyAmount = mBuilder.toString();
				} else if (mInListPrice && localName.equalsIgnoreCase(CURRENCY_CODE)){
					mCurrencyCode = mBuilder.toString();
				} else {
					// Debug Only; see what we are missing.
					//System.out.println(localName + "->'" + mBuilder.toString() + "'");
				}
			}
			mBuilder.setLength(0);			
		} catch (Exception e) {
			Logger.logError(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#startDocument()
	 * 
	 * Start the XML document and the StringBuilder object
	 */
	@Override
	public void startDocument() throws SAXException {
		super.startDocument();
		mBuilder = new StringBuilder();
	}

	/*
	 * (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 * 
	 * Start each XML element. Specifically identify when we are in the item element and set the appropriate flag.
	 */
	@Override
	public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
		super.startElement(uri, localName, name, attributes);
		if (done == false && localName.equalsIgnoreCase(ENTRY)){
			entry = true;
		} else if (localName.equalsIgnoreCase(SMALLIMAGE)) {
			if (mThumbnailSize < 1) {
				image = true;
				mThumbnailSize = 1;
			}
		} else if (localName.equalsIgnoreCase(MEDIUMIMAGE)) {
			if (mThumbnailSize < 2) {
				image = true;
				mThumbnailSize = 2;
			}
		} else if (localName.equalsIgnoreCase(LARGEIMAGE)){
			if (mThumbnailSize < 3) {
				image = true;
				mThumbnailSize = 3;
			}
		} else if (localName.equalsIgnoreCase(LANGUAGE)){
			mInLanguage = true;
		} else if (localName.equalsIgnoreCase(LIST_PRICE)){
			mInListPrice = true;
		}

	}
}
 

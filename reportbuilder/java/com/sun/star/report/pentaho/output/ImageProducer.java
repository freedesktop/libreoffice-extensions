/*************************************************************************
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * 
 * Copyright 2008 by Sun Microsystems, Inc.
 *
 * OpenOffice.org - a multi-platform office productivity suite
 *
 * $RCSfile: ImageProducer.java,v $
 * $Revision: 1.6 $
 *
 * This file is part of OpenOffice.org.
 *
 * OpenOffice.org is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3
 * only, as published by the Free Software Foundation.
 *
 * OpenOffice.org is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License version 3 for more details
 * (a copy is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU Lesser General Public License
 * version 3 along with OpenOffice.org.  If not, see
 * <http://www.openoffice.org/license.html>
 * for a copy of the LGPLv3 License.
 *
 ************************************************************************/
package com.sun.star.report.pentaho.output;

import com.sun.star.report.ImageService;
import com.sun.star.report.InputRepository;
import com.sun.star.report.OutputRepository;
import com.sun.star.report.ReportExecutionException;
import com.sun.star.report.pentaho.DefaultNameGenerator;

import java.awt.Dimension;
import java.awt.Image;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

import java.sql.Blob;
import java.sql.SQLException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.jfree.layouting.input.style.values.CSSNumericType;
import org.jfree.layouting.input.style.values.CSSNumericValue;

import org.pentaho.reporting.libraries.base.util.IOUtils;
import org.pentaho.reporting.libraries.base.util.PngEncoder;
import org.pentaho.reporting.libraries.base.util.WaitingImageObserver;


/**
 * This class manages the images embedded in a report.
 *
 * @author Thomas Morgner
 * @since 31.03.2007
 */
public class ImageProducer
{

    private static final Log LOGGER = LogFactory.getLog(ImageProducer.class);

    public static class OfficeImage
    {

        private final CSSNumericValue width;
        private final CSSNumericValue height;
        private final String embeddableLink;

        public OfficeImage(final String embeddableLink, final CSSNumericValue width, final CSSNumericValue height)
        {
            this.embeddableLink = embeddableLink;
            this.width = width;
            this.height = height;
        }

        public CSSNumericValue getWidth()
        {
            return width;
        }

        public CSSNumericValue getHeight()
        {
            return height;
        }

        public String getEmbeddableLink()
        {
            return embeddableLink;
        }
    }

    private static class ByteDataImageKey
    {

        private final byte[] keyData;
        private Integer hashCode;

        protected ByteDataImageKey(final byte[] keyData)
        {
            if (keyData == null)
            {
                throw new NullPointerException();
            }
            this.keyData = keyData;
        }

        public boolean equals(final Object o)
        {
            if (this != o)
            {
                if (o == null || getClass() != o.getClass())
                {
                    return false;
                }

                final ByteDataImageKey key = (ByteDataImageKey) o;
                if (!Arrays.equals(keyData, key.keyData))
                {
                    return false;
                }
            }

            return true;
        }

        public int hashCode()
        {
            if (hashCode != null)
            {
                return hashCode;
            }

            final int length = Math.min(keyData.length, 512);
            int hashValue = 0;
            for (int i = 0; i < length; i++)
            {
                final byte b = keyData[i];
                hashValue = b + hashValue * 23;
            }
            this.hashCode = hashValue;
            return hashValue;
        }
    }
    private final Map imageCache;
    private final InputRepository inputRepository;
    private final OutputRepository outputRepository;
    private final ImageService imageService;

    public ImageProducer(final InputRepository inputRepository,
            final OutputRepository outputRepository,
            final ImageService imageService)
    {
        if (inputRepository == null)
        {
            throw new NullPointerException();
        }
        if (outputRepository == null)
        {
            throw new NullPointerException();
        }
        if (imageService == null)
        {
            throw new NullPointerException();
        }

        this.inputRepository = inputRepository;
        this.outputRepository = outputRepository;
        this.imageService = imageService;
        this.imageCache = new HashMap();
    }

    /**
     * Image-Data can be one of the following types: String, URL, URI, byte-array, blob.
     *
     * @param imageData
     * @param preserveIRI
     * @return
     */
    public OfficeImage produceImage(final Object imageData,
            final boolean preserveIRI)
    {

        LOGGER.debug("Want to produce image " + imageData);
        if (imageData instanceof String)
        {
            return produceFromString((String) imageData, preserveIRI);
        }

        if (imageData instanceof URL)
        {
            return produceFromURL((URL) imageData, preserveIRI);
        }

        if (imageData instanceof Blob)
        {
            return produceFromBlob((Blob) imageData);
        }

        if (imageData instanceof byte[])
        {
            return produceFromByteArray((byte[]) imageData);
        }

        if (imageData instanceof Image)
        {
            return produceFromImage((Image) imageData);
        }
        // not usable ..
        return null;
    }

    private OfficeImage produceFromImage(final Image image)
    {
        // quick caching ... use a weak list ...
        final WaitingImageObserver obs = new WaitingImageObserver(image);
        obs.waitImageLoaded();

        final PngEncoder encoder = new PngEncoder(image, PngEncoder.ENCODE_ALPHA, PngEncoder.FILTER_NONE, 5);
        final byte[] data = encoder.pngEncode();
        return produceFromByteArray(data);
    }

    private OfficeImage produceFromBlob(final Blob blob)
    {
        try
        {
            final InputStream inputStream = blob.getBinaryStream();
            final int length = (int) blob.length();

            final ByteArrayOutputStream bout = new ByteArrayOutputStream(length);
            try
            {
                IOUtils.getInstance().copyStreams(inputStream, bout);
            } finally
            {
                inputStream.close();
            }
            return produceFromByteArray(bout.toByteArray());
        }
        catch (IOException e)
        {
            LOGGER.warn("Failed to produce image from Blob", e);
        }
        catch (SQLException e)
        {
            LOGGER.warn("Failed to produce image from Blob", e);
        }
        return null;
    }

    private OfficeImage produceFromByteArray(final byte[] data)
    {
        final ByteDataImageKey imageKey = new ByteDataImageKey(data);
        final OfficeImage o = (OfficeImage) imageCache.get(imageKey);
        if (o != null)
        {
            return o;
        }

        try
        {
            final String mimeType = imageService.getMimeType(data);
            final Dimension dims = imageService.getImageSize(data);

            // copy the image into the local output-storage
            // todo: Implement data-fingerprinting so that we can detect the mime-type
            final OutputRepository storage = outputRepository.openOutputRepository("Pictures", null);
            final DefaultNameGenerator nameGenerator = new DefaultNameGenerator(storage);
            final String name = nameGenerator.generateName("image", mimeType);
            final OutputStream outputStream = storage.createOutputStream(name, mimeType);
            final ByteArrayInputStream bin = new ByteArrayInputStream(data);

            try
            {
                IOUtils.getInstance().copyStreams(bin, outputStream);
            } finally
            {
                outputStream.close();
                storage.closeOutputRepository();
            }

            final CSSNumericValue widthVal = CSSNumericValue.createValue(CSSNumericType.MM, dims.getWidth() / 100.0);
            final CSSNumericValue heightVal = CSSNumericValue.createValue(CSSNumericType.MM, dims.getHeight() / 100.0);
            final OfficeImage officeImage = new OfficeImage("Pictures/" + name, widthVal, heightVal);
            imageCache.put(imageKey, officeImage);
            return officeImage;
        }
        catch (IOException e)
        {
            LOGGER.warn("Failed to load image from local input-repository", e);
        }
        catch (ReportExecutionException e)
        {
            LOGGER.warn("Failed to create image from local input-repository", e);
        }
        return null;
    }

    private OfficeImage produceFromString(final String source,
            final boolean preserveIRI)
    {

        try
        {
            final URL url = new URL(source);
            return produceFromURL(url, preserveIRI);
        }
        catch (MalformedURLException e)
        {
            // ignore .. but we had to try this ..
        }

        final OfficeImage o = (OfficeImage) imageCache.get(source);
        if (o != null)
        {
            return o;
        }

        // Next, check whether this is a local path.
        if (inputRepository.isReadable(source))
        {
            // cool, the file exists. Let's try to read it.
            try
            {
                final ByteArrayOutputStream bout = new ByteArrayOutputStream(8192);
                final InputStream inputStream = inputRepository.createInputStream(source);
                try
                {
                    IOUtils.getInstance().copyStreams(inputStream, bout);
                } finally
                {
                    inputStream.close();
                }
                final byte[] data = bout.toByteArray();
                final Dimension dims = imageService.getImageSize(data);
                final String mimeType = imageService.getMimeType(data);

                final CSSNumericValue widthVal = CSSNumericValue.createValue(CSSNumericType.MM, dims.getWidth() / 100.0);
                final CSSNumericValue heightVal = CSSNumericValue.createValue(CSSNumericType.MM, dims.getHeight() / 100.0);

                final String filename = copyToOutputRepository(mimeType, data);
                final OfficeImage officeImage = new OfficeImage(filename, widthVal, heightVal);
                imageCache.put(source, officeImage);
                return officeImage;
            }
            catch (IOException e)
            {
                LOGGER.warn("Failed to load image from local input-repository", e);
            }
            catch (ReportExecutionException e)
            {
                LOGGER.warn("Failed to create image from local input-repository", e);
            }
        }
        else
        {
            try
            {
                URI rootURI = new URI(inputRepository.getRootURL());
                final URI uri = rootURI.resolve(source);
                return produceFromURL(uri.toURL(), preserveIRI);
            }
            catch (URISyntaxException ex)
            {
            }
            catch (MalformedURLException e)
            {
                // ignore .. but we had to try this ..
            }
        }

        // Return the image as broken image instead ..
        final OfficeImage officeImage = new OfficeImage(source, null, null);
        imageCache.put(source, officeImage);
        return officeImage;
    }

    private OfficeImage produceFromURL(final URL url,
            final boolean preserveIRI)
    {
        final String urlString = url.toString();
        URI uri = null;
        try
        {
            uri = new URI(urlString);
        }
        catch (URISyntaxException ex)
        {
            Logger.getLogger(ImageProducer.class.getName()).log(Level.SEVERE, null, ex);
        }
        final OfficeImage o = (OfficeImage) imageCache.get(uri);
        if (o != null)
        {
            return o;
        }

        try
        {
            final ByteArrayOutputStream bout = new ByteArrayOutputStream(8192);
            final URLConnection urlConnection = url.openConnection();
            final InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
            try
            {
                IOUtils.getInstance().copyStreams(inputStream, bout);
            } finally
            {
                inputStream.close();
            }
            final byte[] data = bout.toByteArray();

            final Dimension dims = imageService.getImageSize(data);
            final String mimeType = imageService.getMimeType(data);
            final CSSNumericValue widthVal = CSSNumericValue.createValue(CSSNumericType.MM, dims.getWidth() / 100.0);
            final CSSNumericValue heightVal = CSSNumericValue.createValue(CSSNumericType.MM, dims.getHeight() / 100.0);

            if (preserveIRI)
            {
                final OfficeImage retval = new OfficeImage(urlString, widthVal, heightVal);
                imageCache.put(uri, retval);
                return retval;
            }

            final String name = copyToOutputRepository(mimeType, data);
            final OfficeImage officeImage = new OfficeImage(name, widthVal, heightVal);
            imageCache.put(uri, officeImage);
            return officeImage;
        }
        catch (IOException e)
        {
            LOGGER.warn("Failed to load image from local input-repository" + e);
        }
        catch (ReportExecutionException e)
        {
            LOGGER.warn("Failed to create image from local input-repository" + e);
        }

        if (!preserveIRI)
        {
            final OfficeImage image = new OfficeImage(urlString, null, null);
            imageCache.put(uri, image);
            return image;
        }

        // OK, everything failed; the image is not - repeat it - not usable.
        return null;
    }

    private String copyToOutputRepository(final String urlMimeType, final byte[] data)
            throws IOException, ReportExecutionException
    {
        final String mimeType;
        if (urlMimeType == null)
        {
            mimeType = imageService.getMimeType(data);
        }
        else
        {
            mimeType = urlMimeType;
        }

        // copy the image into the local output-storage
        final OutputRepository storage = outputRepository.openOutputRepository("Pictures", null);
        final DefaultNameGenerator nameGenerator = new DefaultNameGenerator(storage);
        final String name = nameGenerator.generateName("image", mimeType);
        final OutputStream outputStream = storage.createOutputStream(name, mimeType);
        final ByteArrayInputStream bin = new ByteArrayInputStream(data);

        try
        {
            IOUtils.getInstance().copyStreams(bin, outputStream);
        } finally
        {
            outputStream.close();
            storage.closeOutputRepository();
        }
        return "Pictures/" + name;
    }
}

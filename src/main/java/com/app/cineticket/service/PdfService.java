package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Ticket;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Service
public class PdfService{

    public byte[] generateTicketPdf(Ticket ticket) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 24, Font.BOLD);
            Font textFont = new Font(Font.HELVETICA, 14, Font.NORMAL);

            document.add(new Paragraph("CINE TICKET - INGRESSO OFICIAL", titleFont));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Filme: " + ticket.getSession().getMovie().getTitulo(), titleFont));
            document.add(new Paragraph("Data/Hora: " + ticket.getSession().getHorarioInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), textFont));
            document.add(new Paragraph("Sala: " + ticket.getSession().getRoom().getNome(), textFont));
            document.add(new Paragraph("Assento: " + ticket.getSeat().getFila() + "-" + ticket.getSeat().getNumero(), titleFont));

            document.add(new Paragraph(" "));
            document.add(new Paragraph("Comprador: " + ticket.getUser().getNome(), textFont));
            document.add(new Paragraph("Status: " + ticket.getStatus().name(), textFont));
            document.add(new Paragraph("Tipo: " + ticket.getTicketType().name() + " - R$ " + ticket.getValorPago(), textFont));

            document.add(new Paragraph(" "));
            document.add(new Paragraph("--------------------------------------------------", textFont));
            document.add(new Paragraph(" "));

            String qrCodeData = "CINETICKET-VALIDATION-ID:" + ticket.getId() + "-USER:" + ticket.getUser().getEmail();

            com.google.zxing.qrcode.QRCodeWriter qrCodeWriter = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix bitMatrix = qrCodeWriter.encode(
                    qrCodeData, com.google.zxing.BarcodeFormat.QR_CODE, 150, 150);

            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            com.google.zxing.client.j2se.MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);

            com.lowagie.text.Image qrcodeImage = com.lowagie.text.Image.getInstance(pngOutputStream.toByteArray());
            qrcodeImage.setAlignment(com.lowagie.text.Image.ALIGN_CENTER);

            document.add(qrcodeImage);
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Apresente este documento digital na entrada.", textFont));

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar o PDF do ingresso", e);
        }

        return out.toByteArray();
    }
}
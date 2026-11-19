package com.gml.shield.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Représente un document de preuve lié à une mission.
 */
@Entity
@Table(name = "document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    // Nom d'origine reçu du client, conservé pour l'affichage
    // et le téléchargement. Le chemin disque utilise un nom généré côté serveur.
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    // Type MIME détecté à partir du contenu avec Apache Tika, indépendamment
    // de la valeur déclarée par le client.
    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    protected Document() {
        // Constructeur utilisé par JPA.
    }

    public Document(Mission mission, String filePath, String originalFilename, String mimeType) {
        this.mission = mission;
        this.filePath = filePath;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
    }

    public Long getId() { return id; }
    public Mission getMission() { return mission; }
    public String getFilePath() { return filePath; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMimeType() { return mimeType; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
